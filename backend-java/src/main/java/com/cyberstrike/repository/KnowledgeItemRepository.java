package com.cyberstrike.repository;

import com.cyberstrike.entity.KnowledgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, String> {

    List<KnowledgeItem> findByCategoryOrderByTitle(String category);

    List<KnowledgeItem> findAllByOrderByUpdatedAtDesc();

    List<KnowledgeItem> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(String titleKeyword,
            String contentKeyword);

    @Query("SELECT DISTINCT k.category FROM KnowledgeItem k ORDER BY k.category")
    List<String> findDistinctCategories();

    @Query("SELECT COUNT(k) FROM KnowledgeItem k")
    long countAll();

    Optional<KnowledgeItem> findByTitleAndCategory(String title, String category);
}
