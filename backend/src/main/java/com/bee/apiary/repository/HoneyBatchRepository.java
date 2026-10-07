package com.bee.apiary.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.bee.apiary.entity.HoneyBatch;

public interface HoneyBatchRepository extends JpaRepository<HoneyBatch, Long> {

    Optional<HoneyBatch> findByBatchNo(String batchNo);

    /** 行锁读取：质检 / 入库等状态推进必须加锁，防止两人同时提交、重复点击 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HoneyBatch> findWithLockingById(Long id);
}
