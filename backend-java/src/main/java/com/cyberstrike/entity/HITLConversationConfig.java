package com.cyberstrike.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "hitl_conversation_configs")
public class HITLConversationConfig {

    @Id
    @UuidGenerator
    private String id;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private String conversationId;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(nullable = false)
    private String mode = "off";

    @Column(name = "sensitive_tools", columnDefinition = "TEXT", nullable = false)
    private String sensitiveTools = "[]";

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    public void prePersist() {
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSensitiveTools() {
        return sensitiveTools;
    }

    public void setSensitiveTools(String sensitiveTools) {
        this.sensitiveTools = sensitiveTools;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
