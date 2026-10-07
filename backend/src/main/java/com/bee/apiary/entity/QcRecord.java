package com.bee.apiary.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 蜂蜜批次质检记录。
 *
 * <p>每一批次任意时刻至多有一条生效中（active=1）的记录：提交复检时上一条不合格
 * 记录被置为历史（active=0），复检记录成为唯一生效记录。抽样编号全局唯一，
 * 不可在不同批次间复用。</p>
 */
@Entity
@Table(name = "qc_record")
public class QcRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    /** 抽样编号，全局唯一（如 S-2026-001） */
    @Column(name = "sample_no", nullable = false, length = 32, unique = true)
    public String sampleNo;

    /** 初检 / 复检 */
    @Column(name = "round", nullable = false, length = 8)
    public String round;

    /** 含水率 %，放行上限 20% */
    @Column(name = "moisture", nullable = false, precision = 5, scale = 2)
    public BigDecimal moisture;

    /** 感官结论：合格 / 不合格 */
    @Column(name = "sensory", nullable = false, length = 8)
    public String sensory;

    /** 检测人 */
    @Column(name = "inspector", nullable = false, length = 32)
    public String inspector;

    /** 检测时间 */
    @Column(name = "inspect_time", nullable = false)
    public LocalDateTime inspectTime;

    /** 检测说明 / 处理意见 */
    @Column(name = "note", nullable = false, length = 255)
    public String note;

    /** 是否生效中：1 生效（每批次唯一），0 已被复检取代的历史记录 */
    @Column(name = "active", nullable = false)
    public Integer active = 1;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
