package com.cyberstrike.repository;

import com.cyberstrike.entity.BatchTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchTaskRepository extends JpaRepository<BatchTask, String> {
}
