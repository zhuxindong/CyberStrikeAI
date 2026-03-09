package com.cyberstrike.repository;

import com.cyberstrike.entity.BatchQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BatchQueueRepository extends JpaRepository<BatchQueue, String>, JpaSpecificationExecutor<BatchQueue> {
    List<BatchQueue> findAllByOrderByCreatedAtDesc();
}
