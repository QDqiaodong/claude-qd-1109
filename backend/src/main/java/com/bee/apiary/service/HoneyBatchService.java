package com.bee.apiary.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.repository.HiveRepository;
import com.bee.apiary.repository.HoneyBatchRepository;
import com.bee.apiary.repository.QcRecordRepository;
import com.bee.apiary.vo.HoneyBatchDetailVo;
import com.bee.apiary.vo.HoneyBatchSummaryVo;

@Service
public class HoneyBatchService {

    /** 页面支持的质检结论筛选值 */
    public static final Set<String> QC_CONCLUSIONS =
            Set.of("未质检", "初检合格", "初检不合格", "复检合格", "复检不合格");

    @Autowired
    private HoneyBatchRepository repo;

    @Autowired
    private HiveRepository hiveRepository;

    @Autowired
    private QcRecordRepository qcRepo;

    @Autowired
    private HoneyBatchVoAssembler assembler;

    /** 列表：支持按批次状态、质检结论筛选 */
    @Transactional(readOnly = true)
    public List<HoneyBatchSummaryVo> list(String status, String qcConclusion) {
        List<HoneyBatch> batches;
        if (status != null && !status.isEmpty()) {
            if (!Dict.isValidHoneyBatchStatus(status)) {
                throw new BizException("批次状态非法：" + status);
            }
            batches = repo.findAll().stream().filter(b -> status.equals(b.status)).toList();
        } else {
            batches = repo.findAll();
        }
        List<HoneyBatchSummaryVo> vos = assembler.toSummaries(batches);
        if (qcConclusion != null && !qcConclusion.isEmpty()) {
            if (!QC_CONCLUSIONS.contains(qcConclusion)) {
                throw new BizException("质检结论非法：" + qcConclusion);
            }
            vos = vos.stream().filter(v -> qcConclusion.equals(v.qcConclusion)).toList();
        }
        return vos;
    }

    /** 实体读取（业务内部用） */
    public HoneyBatch getEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("采蜜批次不存在：" + id));
    }

    /** 详情：批次 + 可回溯的质检历史与整改历史 + 动作可执行性 */
    @Transactional(readOnly = true)
    public HoneyBatchDetailVo getDetail(Long id) {
        return assembler.toDetail(getEntity(id));
    }

    @Transactional
    public HoneyBatch create(HoneyBatch input) {
        if (input.hiveId == null) {
            throw new BizException("请选择蜂箱");
        }
        if (!hiveRepository.existsById(input.hiveId)) {
            throw new BizException("蜂箱不存在：" + input.hiveId);
        }
        if (input.batchNo == null || input.batchNo.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        if (repo.findByBatchNo(input.batchNo).isPresent()) {
            throw new BizException("批次号已存在：" + input.batchNo);
        }
        if (input.weightKg == null || input.weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("重量必须大于 0");
        }
        HoneyBatch saved = new HoneyBatch();
        saved.hiveId = input.hiveId;
        saved.batchNo = input.batchNo;
        saved.weightKg = input.weightKg;
        saved.status = "待质检";
        return repo.save(saved);
    }

    /**
     * 编辑批次基础信息：仅「待质检」且尚无质检记录的批次允许修改；
     * 已进入质检流转的批次只能通过质检 / 整改 / 复检 / 入库动作推进，
     * 防止旧页面把已放行 / 已入库批次覆盖回退。
     */
    @Transactional
    public HoneyBatch update(Long id, HoneyBatch input) {
        HoneyBatch existing = getEntity(id);
        if (!"待质检".equals(existing.status)) {
            throw new BizException("批次已进入质检流转（当前：" + existing.status + "），基础信息不可修改");
        }
        if (qcRepo.existsByBatchId(id)) {
            throw new BizException("批次已存在质检记录，基础信息不可修改");
        }
        if (input.hiveId != null && !input.hiveId.equals(existing.hiveId)) {
            if (!hiveRepository.existsById(input.hiveId)) {
                throw new BizException("蜂箱不存在：" + input.hiveId);
            }
            existing.hiveId = input.hiveId;
        }
        if (input.batchNo != null && !input.batchNo.equals(existing.batchNo)) {
            if (input.batchNo.isBlank()) {
                throw new BizException("批次号不能为空");
            }
            HoneyBatch other = repo.findByBatchNo(input.batchNo).orElse(null);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("批次号已存在：" + input.batchNo);
            }
            existing.batchNo = input.batchNo;
        }
        if (input.weightKg != null) {
            if (input.weightKg.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("重量必须大于 0");
            }
            existing.weightKg = input.weightKg;
        }
        // 状态只能走质检放行与入库动作，这里不允许直接改 status
        return repo.save(existing);
    }

    /**
     * 状态机：已放行 → 已入库。
     * 未放行（待质检 / 隔离）一律禁止入库；加行锁保证重复点击只生效一次。
     */
    @Transactional
    public HoneyBatch stockIn(Long id) {
        HoneyBatch existing = repo.findWithLockingById(id)
                .orElseThrow(() -> new BizException("采蜜批次不存在：" + id));
        if ("已作废".equals(existing.status)) {
            throw new BizException("已作废批次不可入库");
        }
        if ("隔离待整改".equals(existing.status)) {
            throw new BizException("批次隔离待整改，整改复检合格放行前禁止入库");
        }
        if ("隔离拒收".equals(existing.status)) {
            throw new BizException("复检不合格已拒绝入库");
        }
        if ("已入库".equals(existing.status)) {
            throw new BizException("批次已入库，请勿重复操作");
        }
        if (!Dict.canTransferHoneyBatch(existing.status, "已入库")) {
            throw new BizException("只有质检合格「已放行」的批次才能入库");
        }
        existing.status = "已入库";
        return repo.save(existing);
    }

    /** 删除：仅尚无质检记录的待质检批次可删，保证质检台账可回溯 */
    @Transactional
    public void delete(Long id) {
        HoneyBatch existing = getEntity(id);
        if (qcRepo.existsByBatchId(id)) {
            throw new BizException("批次已存在质检记录，为保证质检台账可回溯不可删除");
        }
        if (!"待质检".equals(existing.status)) {
            throw new BizException("仅「待质检」批次可删除，当前状态：" + existing.status);
        }
        repo.deleteById(id);
    }
}
