package com.cyberstrike.repository;

import com.cyberstrike.entity.ToolExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolExecutionRepository extends JpaRepository<ToolExecution, String> {

    List<ToolExecution> findAllByOrderByCreatedAtDesc();

    List<ToolExecution> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    @Query("SELECT t.toolName, COUNT(t), AVG(t.durationMs) FROM ToolExecution t GROUP BY t.toolName")
    List<Object[]> getToolStats();

    @Query("SELECT t.status, COUNT(t) FROM ToolExecution t GROUP BY t.status")
    List<Object[]> countByStatus();
}
