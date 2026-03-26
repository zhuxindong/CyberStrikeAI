package com.cyberstrike.repository;

import com.cyberstrike.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 【Go -> Java 迁移新增】知识块仓库
 * 对应数据库表：knowledge_embeddings
 */
@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {

    /**
     * 根据知识项ID获取所有块
     */
    List<KnowledgeChunk> findByItemIdOrderByChunkIndex(String itemId);

    /**
     * 根据知识项ID删除所有块
     */
    void deleteByItemId(String itemId);

    /**
     * 获取所有块（用于检索）
     */
    @Query("SELECT c FROM KnowledgeChunk c JOIN KnowledgeItem i ON c.itemId = i.id")
    List<KnowledgeChunk> findAllChunks();

    /**
     * 根据分类获取所有块
     */
    @Query("SELECT c FROM KnowledgeChunk c JOIN KnowledgeItem i ON c.itemId = i.id WHERE TRIM(LOWER(i.category)) = TRIM(LOWER(:category))")
    List<KnowledgeChunk> findByCategory(String category);

    /**
     * 统计块数量
     */
    long count();
}
