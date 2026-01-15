package com.cyberstrike.repository;

import com.cyberstrike.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
