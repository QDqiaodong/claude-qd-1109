package com.bee.apiary.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.entity.QcRecord;
import com.bee.apiary.entity.Rectification;
import com.bee.apiary.repository.HoneyBatchRepository;
import com.bee.apiary.repository.QcRecordRepository;
import com.bee.apiary.repository.RectificationRepository;
import com.bee.apiary.vo.HoneyBatchDetailVo;

/**
 * 蜂蜜批次质检放行：登记抽样检测结果（初检 / 复检）、登记隔离整改说明。
 *
 * <p>并发口径：所有动作先对批次加行锁（PESSIMISTIC_WRITE），再校验当前状态与
 * 历史记录——两个工作人员同时提交或重复点击时只有一笔能成功；抽样编号唯一、
 * 每批次至多一条生效质检记录另有数据库唯一约束兜底。</p>
 */
@Service
public class QcService {

    @Autowired
    private HoneyBatchRepository batchRepo;

    @Autowired
    private QcRecordRepository qcRepo;

    @Autowired
    private RectificationRepository rectRepo;

    @Autowired
    private HoneyBatchVoAssembler assembler;

    /**
     * 登记初检结果。
     * 合格（含水率 ≤ 20% 且感官合格）→ 批次放行；任一不合格 → 隔离待整改。
     */
    @Transactional
    public HoneyBatchDetailVo submitInitial(Long batchId, QcRecord input) {
        HoneyBatch batch = lockBatch(batchId);
        if ("已作废".equals(batch.status)) {
            throw new BizException("已作废批次不可质检");
        }
        if (!"待质检".equals(batch.status)) {
            throw new BizException("仅「待质检」批次可登记初检，当前状态：" + batch.status
                    + "（页面数据可能已过期，请刷新后重试）");
        }
        validateQcInput(input);
        if (qcRepo.findBySampleNo(input.sampleNo.trim()).isPresent()) {
            throw new BizException("抽样编号已被使用，不能在不同批次间重复使用：" + input.sampleNo.trim());
        }
        // DB 唯一索引 (batch_id, active) 兜底：同一批次不能同时存在两条生效记录
        if (qcRepo.findByBatchIdAndActive(batchId, 1).isPresent()) {
            throw new BizException("该批次已存在生效中的质检记录，不可重复登记");
        }

        boolean pass = Dict.isPass(input.moisture, input.sensory);
        QcRecord record = buildRecord(batchId, input, "初检");
        qcRepo.save(record);

        moveBatch(batch, pass ? "已放行" : "隔离待整改");
        return assembler.toDetail(batch);
    }

    /**
     * 登记隔离整改说明。未补充整改说明前批次不能再次送检（复检按钮禁用）。
     */
    @Transactional
    public HoneyBatchDetailVo submitRectification(Long batchId, Rectification input) {
        HoneyBatch batch = lockBatch(batchId);
        if (!"隔离待整改".equals(batch.status)) {
            throw new BizException("仅「隔离待整改」批次可登记整改说明，当前状态：" + batch.status
                    + "（页面数据可能已过期，请刷新后重试）");
        }
        QcRecord active = activeFailedRecord(batchId);
        if (rectRepo.findByQcRecordId(active.id).isPresent()) {
            throw new BizException("该不合格质检的整改说明已登记，请勿重复提交");
        }
        if (input == null || input.content == null || input.content.isBlank()) {
            throw new BizException("请补充整改说明");
        }
        if (input.handler == null || input.handler.isBlank()) {
            throw new BizException("请填写整改登记人");
        }

        Rectification rect = new Rectification();
        rect.batchId = batchId;
        rect.qcRecordId = active.id;
        rect.content = input.content.trim();
        rect.handler = input.handler.trim();
        rectRepo.save(rect);
        return assembler.toDetail(batch);
    }

    /**
     * 整改后发起复检（最多一次）。
     * 合格 → 已放行（可入库）；仍不合格 → 隔离拒收，保持隔离并明确拒绝入库。
     */
    @Transactional
    public HoneyBatchDetailVo submitReinspection(Long batchId, QcRecord input) {
        HoneyBatch batch = lockBatch(batchId);
        if ("已作废".equals(batch.status)) {
            throw new BizException("已作废批次不可复检");
        }
        if (!"隔离待整改".equals(batch.status)) {
            throw new BizException("仅「隔离待整改」批次可发起复检，当前状态：" + batch.status
                    + "（页面数据可能已过期，请刷新后重试）");
        }
        QcRecord active = activeFailedRecord(batchId);
        if (rectRepo.findByQcRecordId(active.id).isEmpty()) {
            throw new BizException("须先补充整改说明后才能再次送检");
        }
        if (qcRepo.findByBatchIdOrderByInspectTimeAscIdAsc(batchId).stream()
                .anyMatch(q -> "复检".equals(q.round))) {
            throw new BizException("该批次已发起过复检，按规定最多复检一次");
        }
        validateQcInput(input);
        if (qcRepo.findBySampleNo(input.sampleNo.trim()).isPresent()) {
            throw new BizException("抽样编号已被使用，不能在不同批次间重复使用：" + input.sampleNo.trim());
        }

        boolean pass = Dict.isPass(input.moisture, input.sensory);

        // 旧的不合格初检记录转为历史，保证同一批次始终只有一条生效中记录。
        // 必须先 flush 让 UPDATE 落库，再插入新生效记录，否则会撞 (batch_id, active) 唯一索引。
        active.active = 0;
        qcRepo.save(active);
        qcRepo.flush();

        QcRecord record = buildRecord(batchId, input, "复检");
        qcRepo.save(record);

        // 复检合格 → 已放行；复检仍不合格 → 隔离拒收（终态，拒绝入库）
        moveBatch(batch, pass ? "已放行" : "隔离拒收");
        return assembler.toDetail(batch);
    }

    // ---------------- 内部方法 ----------------

    private HoneyBatch lockBatch(Long batchId) {
        return batchRepo.findWithLockingById(batchId)
                .orElseThrow(() -> new BizException("采蜜批次不存在：" + batchId));
    }

    private QcRecord activeFailedRecord(Long batchId) {
        QcRecord active = qcRepo.findByBatchIdAndActive(batchId, 1)
                .orElseThrow(() -> new BizException("该批次没有生效中的不合格质检记录"));
        if (Dict.isPass(active.moisture, active.sensory)) {
            throw new BizException("当前生效质检为合格结论，无需整改 / 复检");
        }
        return active;
    }

    private void validateQcInput(QcRecord input) {
        if (input == null) {
            throw new BizException("请填写质检结果");
        }
        if (input.sampleNo == null || input.sampleNo.isBlank()) {
            throw new BizException("抽样编号不能为空");
        }
        if (input.sampleNo.trim().length() > 32) {
            throw new BizException("抽样编号长度不能超过 32 个字符");
        }
        if (input.moisture == null) {
            throw new BizException("含水率不能为空");
        }
        if (input.moisture.compareTo(BigDecimal.ZERO) < 0
                || input.moisture.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException("含水率取值应为 0~100 之间的百分数");
        }
        if (!Dict.isValidSensory(input.sensory)) {
            throw new BizException("感官结论取值非法：" + input.sensory);
        }
        if (input.inspector == null || input.inspector.isBlank()) {
            throw new BizException("请填写检测人");
        }
        if (input.inspectTime == null) {
            throw new BizException("请填写检测时间");
        }
        if (input.note == null || input.note.isBlank()) {
            throw new BizException("请填写检测说明（含处理意见）");
        }
    }

    private QcRecord buildRecord(Long batchId, QcRecord input, String round) {
        QcRecord record = new QcRecord();
        record.batchId = batchId;
        record.sampleNo = input.sampleNo.trim();
        record.round = round;
        record.moisture = input.moisture;
        record.sensory = input.sensory;
        record.inspector = input.inspector.trim();
        record.inspectTime = input.inspectTime;
        record.note = input.note.trim();
        record.active = 1;
        return record;
    }

    private void moveBatch(HoneyBatch batch, String target) {
        if (!Dict.canTransferHoneyBatch(batch.status, target)) {
            throw new BizException("批次状态不可由「" + batch.status + "」变更为「" + target + "」");
        }
        batch.status = target;
        batchRepo.save(batch);
    }
}
