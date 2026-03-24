package com.cyberstrike.repository;

import com.cyberstrike.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    // Get messages for a conversation, ordered by creation time
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    // Delete all messages for a conversation
    @Transactional
    void deleteByConversationId(String conversationId);

    Page<Message> findByTypeAndDelFlagNotAndToolTypeAndMcpExecutionIdsContainingIgnoreCaseAndResultStatusContainingIgnoreCase(
            String type,
            int delFlag,
            String toolType,
            String mcpExecutionIds,
            String resultStatus,
            Pageable pageable
    );
    List<Message> findByToolIdInAndType(List<String> callIds, String tool_result);

    @Modifying
    @Query("UPDATE Message m SET m.delFlag = 1 WHERE m.id IN :idList OR m.toolId IN :idList")
    int deleteByToolIds(@Param("idList") List<String> idList);
}
