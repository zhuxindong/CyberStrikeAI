package com.cyberstrike.repository;

import com.cyberstrike.entity.HITLConversationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface HITLConversationConfigRepository extends JpaRepository<HITLConversationConfig, String> {

    Optional<HITLConversationConfig> findByConversationId(String conversationId);

    @Modifying
    @Query("UPDATE HITLConversationConfig h SET h.enabled = :enabled, h.mode = :mode, h.sensitiveTools = :sensitiveTools, h.timeoutSeconds = :timeoutSeconds, h.updatedAt = :updatedAt WHERE h.conversationId = :conversationId")
    int updateConfig(@Param("conversationId") String conversationId, @Param("enabled") Boolean enabled, @Param("mode") String mode, @Param("sensitiveTools") String sensitiveTools, @Param("timeoutSeconds") Integer timeoutSeconds, @Param("updatedAt") LocalDateTime updatedAt);
}
