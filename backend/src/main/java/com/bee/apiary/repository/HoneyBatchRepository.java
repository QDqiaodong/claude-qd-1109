package com.bee.apiary.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.HoneyBatch;

public interface HoneyBatchRepository extends JpaRepository<HoneyBatch, Long> {

    Optional<HoneyBatch> findByBatchNo(String batchNo);
}
