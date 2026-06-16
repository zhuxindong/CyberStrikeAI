package com.cyberstrike.repository;

import com.cyberstrike.entity.HITLInterrupt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HITLInterruptRepository extends JpaRepository<HITLInterrupt, String> {

    Page<HITLInterrupt> findByConversationIdAndStatusOrderByCreatedAtDesc(String conversationId, String status, Pageable pageable);

    // 查询所有会话的 pending 中断（新增）
    Page<HITLInterrupt> findByStatus(String status, Pageable pageable);

    List<HITLInterrupt> findByConversationIdAndStatusOrderByCreatedAtDesc(String conversationId, String status);

    List<HITLInterrupt> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    Optional<HITLInterrupt> findTopByConversationIdAndStatusOrderByCreatedAtDesc(String conversationId, String status);

    @Modifying
    @Query("UPDATE HITLInterrupt h SET h.status = 'cancelled', h.decision = 'reject', h.decisionComment = :comment, h.decidedAt = :decidedAt WHERE h.status = 'pending'")
    int cancelAllPending(@Param("comment") String comment, @Param("decidedAt") LocalDateTime decidedAt);

    @Modifying
    @Query("UPDATE HITLInterrupt h SET h.status = :status, h.decision = :decision, h.decisionComment = :comment, h.decidedAt = :decidedAt WHERE h.id = :id AND h.status = 'pending'")
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("decision") String decision, @Param("comment") String comment, @Param("decidedAt") LocalDateTime decidedAt);
}
