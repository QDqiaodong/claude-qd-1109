package com.bee.apiary.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "honey_batch")
public class HoneyBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "hive_id", nullable = false)
    public Long hiveId;

    @Column(name = "batch_no", nullable = false, length = 32, unique = true)
    public String batchNo;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 2)
    public BigDecimal weightKg;

    @Column(name = "status", length = 16)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
