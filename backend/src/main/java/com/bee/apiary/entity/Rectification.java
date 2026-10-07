package com.bee.apiary.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 隔离批次整改说明。
 *
 * <p>质检不合格后批次进入「隔离待整改」，必须先登记整改说明才允许发起复检；
 * 每条不合格（生效中）质检记录对应至多一条整改说明。</p>
 */
@Entity
@Table(name = "rectification")
public class Rectification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    /** 针对的不合格质检记录 */
    @Column(name = "qc_record_id", nullable = false)
    public Long qcRecordId;

    /** 整改说明 */
    @Column(name = "content", nullable = false, length = 500)
    public String content;

    /** 整改登记人 */
    @Column(name = "handler", nullable = false, length = 32)
    public String handler;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
