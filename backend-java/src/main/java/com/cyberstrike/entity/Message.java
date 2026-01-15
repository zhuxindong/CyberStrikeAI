package com.cyberstrike.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @UuidGenerator
    private String id;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String role;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(name = "mcp_execution_ids", columnDefinition = "TEXT")
    private String mcpExecutionIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Message() {
    }

    public Message(String conversationId, String role, String content) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
    }

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.createdAt == null)
            this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMcpExecutionIds() {
        return mcpExecutionIds;
    }

    public void setMcpExecutionIds(String mcpExecutionIds) {
        this.mcpExecutionIds = mcpExecutionIds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
