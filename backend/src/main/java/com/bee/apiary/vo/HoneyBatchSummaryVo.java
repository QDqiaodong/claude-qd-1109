package com.bee.apiary.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.bee.apiary.entity.QcRecord;

/**
 * 采蜜批次质检放行视图：在批次基础字段上附带当前生效质检、质检结论标签，
 * 以及四个动作（登记质检 / 整改 / 复检 / 入库）是否可执行与不可执行原因，
 * 前端据此把放行、复检等不可执行动作明确说明并禁用。
 */
public class HoneyBatchSummaryVo {

    public Long id;
    public Long hiveId;
    public String batchNo;
    public BigDecimal weightKg;
    public String status;
    public LocalDateTime createdAt;

    /** 质检记录条数（含已被复检取代的历史记录，用于回溯） */
    public int qcCount;

    /** 当前生效中的质检记录 */
    public QcRecord activeQc;

    /**
     * 质检结论标签：未质检 / 初检合格 / 初检不合格 / 复检合格 / 复检不合格
     */
    public String qcConclusion;

    public boolean canRegisterQc;
    public String registerQcReason;

    public boolean canRectify;
    public String rectifyReason;

    public boolean canReinspect;
    public String reinspectReason;

    public boolean canStockIn;
    public String stockInReason;
}
