package com.cyberstrike.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【Go -> Java 迁移新增】知识块实体
 * 对齐 Go 结构体：internal/knowledge/types.go KnowledgeChunk
 * 对应数据库表：knowledge_embeddings
 */
@Entity
@Table(name = "knowledge_embeddings")
public class KnowledgeChunk {

    @Id
    private String id;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_text", columnDefinition = "LONGTEXT")
    private String chunkText;

    @Column(columnDefinition = "JSON")
    private String embedding; // JSON array of floats

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
