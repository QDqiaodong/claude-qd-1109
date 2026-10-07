package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.QcRecord;

public interface QcRecordRepository extends JpaRepository<QcRecord, Long> {

    Optional<QcRecord> findBySampleNo(String sampleNo);

    /** 该批次当前生效中的质检记录（DB 层保证至多一条） */
    Optional<QcRecord> findByBatchIdAndActive(Long batchId, Integer active);

    List<QcRecord> findByBatchIdOrderByInspectTimeAscIdAsc(Long batchId);

    List<QcRecord> findByBatchIdInOrderByBatchIdAscInspectTimeAscIdAsc(List<Long> batchIds);

    boolean existsByBatchId(Long batchId);
}
