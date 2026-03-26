package com.cyberstrike.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【Go -> Java 迁移新增】知识检索日志
 * 对齐 Go 结构体：internal/knowledge/types.go RetrievalLog
 */
@Entity
@Table(name = "knowledge_retrieval_logs")
@Data
public class RetrievalLog {

    @Id
    private String id;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "message_id")
    private String messageId;

    private String query;

    @Column(name = "risk_type")
    private String riskType;

    @Column(name = "retrieved_items", columnDefinition = "TEXT")
    private String retrievedItems; // JSON array of item IDs

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
