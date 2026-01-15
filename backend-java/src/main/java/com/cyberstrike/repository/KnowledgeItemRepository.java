package com.cyberstrike.repository;

import com.cyberstrike.entity.KnowledgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, String> {

    List<KnowledgeItem> findByCategoryOrderByCreatedAtDesc(String category);

    List<KnowledgeItem> findByTitleContainingOrContentContainingOrderByCreatedAtDesc(String titleKeyword,
            String contentKeyword);

    @Query("SELECT DISTINCT k.category FROM KnowledgeItem k")
    List<String> findDistinctCategories();

    @Query("SELECT COUNT(k) FROM KnowledgeItem k WHERE k.embedding IS NOT NULL AND k.embedding != ''")
    long countIndexed();
}
