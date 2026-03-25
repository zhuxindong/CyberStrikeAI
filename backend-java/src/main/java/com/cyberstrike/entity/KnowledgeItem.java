package com.cyberstrike.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【Go -> Java 迁移】知识库项实体
 * 对齐 Go 结构体：internal/knowledge/types.go KnowledgeItem
 * 对应数据库表：knowledge_base_items
 */
@Entity
@Table(name = "knowledge_base_items")
public class KnowledgeItem {

    @Id
    private String id;

    private String category;

    private String title;

    @Column(name = "file_path")
    private String filePath;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}
