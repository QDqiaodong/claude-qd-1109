package com.bee.apiary.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.entity.QcRecord;
import com.bee.apiary.entity.Rectification;
import com.bee.apiary.repository.QcRecordRepository;
import com.bee.apiary.repository.RectificationRepository;
import com.bee.apiary.vo.HoneyBatchDetailVo;
import com.bee.apiary.vo.HoneyBatchSummaryVo;

/**
 * 批次质检放行视图装配：批量加载质检 / 整改历史，计算质检结论标签与
 * 各动作（登记质检、整改、复检、入库）的可执行性及不可执行原因。
 */
@Component
public class HoneyBatchVoAssembler {

    @Autowired
    private QcRecordRepository qcRepo;

    @Autowired
    private RectificationRepository rectRepo;

    public List<HoneyBatchSummaryVo> toSummaries(List<HoneyBatch> batches) {
        if (batches.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = batches.stream().map(b -> b.id).toList();

        Map<Long, List<QcRecord>> qcMap = new HashMap<>();
        qcRepo.findByBatchIdInOrderByBatchIdAscInspectTimeAscIdAsc(ids)
                .forEach(q -> qcMap.computeIfAbsent(q.batchId, k -> new ArrayList<>()).add(q));

        Map<Long, List<Rectification>> rectMap = new HashMap<>();
        rectRepo.findByBatchIdInOrderByBatchIdAscCreatedAtAscIdAsc(ids)
                .forEach(r -> rectMap.computeIfAbsent(r.batchId, k -> new ArrayList<>()).add(r));

        List<HoneyBatchSummaryVo> result = new ArrayList<>();
        for (HoneyBatch b : batches) {
            result.add(fill(new HoneyBatchSummaryVo(), b,
                    qcMap.getOrDefault(b.id, Collections.emptyList()),
                    rectMap.getOrDefault(b.id, Collections.emptyList())));
        }
        return result;
    }

    public HoneyBatchDetailVo toDetail(HoneyBatch batch) {
        List<QcRecord> qcs = qcRepo.findByBatchIdOrderByInspectTimeAscIdAsc(batch.id);
        List<Rectification> rects = rectRepo.findByBatchIdOrderByCreatedAtAscIdAsc(batch.id);
        return fill(new HoneyBatchDetailVo(), batch, qcs, rects);
    }

    private <V extends HoneyBatchSummaryVo> V fill(V vo, HoneyBatch batch,
                                                   List<QcRecord> qcs,
                                                   List<Rectification> rects) {
        vo.id = batch.id;
        vo.hiveId = batch.hiveId;
        vo.batchNo = batch.batchNo;
        vo.weightKg = batch.weightKg;
        vo.status = batch.status;
        vo.createdAt = batch.createdAt;
        vo.qcCount = qcs.size();

        QcRecord active = null;
        for (QcRecord q : qcs) {
            if (q.active != null && q.active == 1) {
                active = q;
            }
        }
        final QcRecord activeRecord = active;
        vo.activeQc = activeRecord;
        vo.qcConclusion = conclusion(activeRecord);

        boolean activeFailed = activeRecord != null && !Dict.isPass(activeRecord.moisture, activeRecord.sensory);
        boolean rectified = activeRecord != null
                && rects.stream().anyMatch(r -> r.qcRecordId.equals(activeRecord.id));
        boolean hadReinspection = qcs.stream().anyMatch(q -> "复检".equals(q.round));

        // ---- 登记质检（初检）----
        vo.canRegisterQc = "待质检".equals(batch.status);
        vo.registerQcReason = registerQcReason(batch.status);

        // ---- 登记整改说明 ----
        vo.canRectify = "隔离待整改".equals(batch.status) && activeFailed && !rectified;
        vo.rectifyReason = rectifyReason(batch.status, activeFailed, rectified);

        // ---- 发起复检（整改后最多一次）----
        vo.canReinspect = "隔离待整改".equals(batch.status)
                && activeFailed && rectified && !hadReinspection;
        vo.reinspectReason =
                reinspectReason(batch.status, activeFailed, rectified, hadReinspection);

        // ---- 入库 ----
        vo.canStockIn = "已放行".equals(batch.status);
        vo.stockInReason = stockInReason(batch.status);

        if (vo instanceof HoneyBatchDetailVo detail) {
            detail.qcRecords = qcs;
            detail.rectifications = rects;
        }
        return vo;
    }

    /** 质检结论标签：未质检 / 初检合格 / 初检不合格 / 复检合格 / 复检不合格 */
    private String conclusion(QcRecord active) {
        if (active == null) {
            return "未质检";
        }
        boolean pass = Dict.isPass(active.moisture, active.sensory);
        return active.round + (pass ? "合格" : "不合格");
    }

    private String registerQcReason(String status) {
        return switch (status) {
            case "待质检" -> null;
            case "已放行", "已入库" -> "该批次已质检放行，不能再次送检";
            case "隔离待整改" -> "初检不合格，须先登记整改说明并走复检，不能重复初检";
            case "隔离拒收" -> "复检仍不合格已拒绝入库，不能再次送检";
            case "已作废" -> "已作废批次不可质检";
            default -> "当前状态不可质检";
        };
    }

    private String rectifyReason(String status, boolean activeFailed, boolean rectified) {
        if (!"隔离待整改".equals(status)) {
            return switch (status) {
                case "待质检" -> "批次尚未质检，无不合格项需要整改";
                case "已放行", "已入库" -> "批次已放行，无需整改";
                case "隔离拒收" -> "复检已结束，整改窗口关闭";
                case "已作废" -> "已作废批次不可整改";
                default -> "当前状态不可整改";
            };
        }
        if (!activeFailed) {
            return "当前生效质检为合格结论，无需整改";
        }
        if (rectified) {
            return "整改说明已登记，请直接发起复检";
        }
        return null;
    }

    private String reinspectReason(String status, boolean activeFailed,
                                   boolean rectified, boolean hadReinspection) {
        if (!"隔离待整改".equals(status)) {
            return switch (status) {
                case "待质检" -> "须先完成初检，初检不合格隔离后才可复检";
                case "已放行", "已入库" -> "批次已放行，无需复检";
                case "隔离拒收" -> "复检已使用且仍不合格，按规定最多复检一次，保持隔离";
                case "已作废" -> "已作废批次不可复检";
                default -> "当前状态不可复检";
            };
        }
        if (!activeFailed) {
            return "当前生效质检为合格结论，无需复检";
        }
        if (!rectified) {
            return "须先补充整改说明后才能再次送检";
        }
        if (hadReinspection) {
            return "已发起过复检，按规定最多复检一次";
        }
        return null;
    }

    private String stockInReason(String status) {
        return switch (status) {
            case "已放行" -> null;
            case "待质检" -> "须质检合格放行后才能入库";
            case "隔离待整改" -> "批次隔离待整改，整改复检合格前禁止入库";
            case "隔离拒收" -> "复检仍不合格，已明确拒绝入库";
            case "已入库" -> "批次已入库，请勿重复操作";
            case "已作废" -> "已作废批次不可入库";
            default -> "当前状态不可入库";
        };
    }
}
