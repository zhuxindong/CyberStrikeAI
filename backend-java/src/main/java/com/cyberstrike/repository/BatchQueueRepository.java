package com.cyberstrike.repository;

import com.cyberstrike.entity.BatchQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BatchQueueRepository extends JpaRepository<BatchQueue, String> {
    List<BatchQueue> findAllByOrderByCreatedAtDesc();
}
