package com.bee.apiary.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.Hive;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.repository.HiveRepository;
import com.bee.apiary.repository.HoneyBatchRepository;

@Service
public class HoneyBatchService {

    @Autowired
    private HoneyBatchRepository repo;

    @Autowired
    private HiveRepository hiveRepository;

    public java.util.List<HoneyBatch> list(String status) {
        if (status != null && !status.isEmpty()) {
            if (!Dict.isValidHoneyBatchStatus(status)) {
                throw new BizException("批次状态非法：" + status);
            }
            return repo.findAll().stream().filter(b -> status.equals(b.status)).toList();
        }
        return repo.findAll();
    }

    public HoneyBatch get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("采蜜批次不存在：" + id));
    }

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

    public HoneyBatch update(Long id, HoneyBatch input) {
        HoneyBatch existing = get(id);
        if ("已作废".equals(existing.status)) {
            throw new BizException("已作废批次不可修改");
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
        // 状态流转只能走 stock-in 动作，这里不允许直接改 status
        return repo.save(existing);
    }

    /** 状态机：待质检 → 已入库；已作废不可再流转 */
    public HoneyBatch stockIn(Long id) {
        HoneyBatch existing = get(id);
        if ("已作废".equals(existing.status)) {
            throw new BizException("已作废批次不可入库");
        }
        if (!Dict.canTransferHoneyBatch(existing.status, "已入库")) {
            throw new BizException("只有「待质检」批次才能入库");
        }
        existing.status = "已入库";
        return repo.save(existing);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new BizException("采蜜批次不存在：" + id);
        }
        repo.deleteById(id);
    }
}
