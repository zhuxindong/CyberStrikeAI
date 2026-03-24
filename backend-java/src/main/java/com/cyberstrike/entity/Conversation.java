package com.cyberstrike.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_react_input", columnDefinition = "TEXT")
    private String lastReactInput;

    @Column(name = "last_react_output", columnDefinition = "TEXT")
    private String lastReactOutput;

    @Column(nullable = false)
    private Boolean pinned = false;

    @Column(nullable = false)
    private String status = "pending"; // pending, running, completed, pkg_failed, error

    @Column(name = "task_id")
    private String taskId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * 【Go -> Java 迁移新增】关联的 WebShell 连接 ID
     * 用于 WebShell AI 助手持久化对话
     */
    @Column(name = "webshell_connection_id")
    private String webshellConnectionId;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.createdAt == null)
            this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null)
            this.updatedAt = LocalDateTime.now();
        if (this.pinned == null)
            this.pinned = false;
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLastReactInput() {
        return lastReactInput;
    }

    public void setLastReactInput(String lastReactInput) {
        this.lastReactInput = lastReactInput;
    }

    public String getLastReactOutput() {
        return lastReactOutput;
    }

    public void setLastReactOutput(String lastReactOutput) {
        this.lastReactOutput = lastReactOutput;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWebshellConnectionId() {
        return webshellConnectionId;
    }

    public void setWebshellConnectionId(String webshellConnectionId) {
        this.webshellConnectionId = webshellConnectionId;
    }
}
