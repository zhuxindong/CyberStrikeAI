package com.cyberstrike.repository;

import com.cyberstrike.entity.RetrievalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 【Go -> Java 迁移新增】知识检索日志仓库
 * 对应数据库表：knowledge_retrieval_logs
 */
@Repository
public interface RetrievalLogRepository extends JpaRepository<RetrievalLog, String> {

    List<RetrievalLog> findByMessageIdOrderByCreatedAtDesc(String messageId);

    List<RetrievalLog> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    // 使用原生 SQL 查询
    @Query(value = "SELECT * FROM knowledge_retrieval_logs ORDER BY created_at DESC LIMIT ?1", nativeQuery = true)
    List<RetrievalLog> findTopByOrderByCreatedAtDesc(int limit);
}
