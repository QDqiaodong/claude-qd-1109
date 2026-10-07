package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.Rectification;

public interface RectificationRepository extends JpaRepository<Rectification, Long> {

    Optional<Rectification> findByQcRecordId(Long qcRecordId);

    List<Rectification> findByBatchIdOrderByCreatedAtAscIdAsc(Long batchId);

    List<Rectification> findByBatchIdInOrderByBatchIdAscCreatedAtAscIdAsc(List<Long> batchIds);
}
