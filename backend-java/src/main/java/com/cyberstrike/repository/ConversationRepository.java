package com.cyberstrike.repository;

import com.cyberstrike.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    // Basic CRUD is provided by JpaRepository

    // For list with search (pageable)
    Page<Conversation> findByTitleContainingOrderByUpdatedAtDesc(String title, Pageable pageable);

    // For default list (pageable)
    Page<Conversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    // Simple list methods without pagination
    List<Conversation> findByTitleContainingOrderByUpdatedAtDesc(String title);

    List<Conversation> findAllByOrderByUpdatedAtDesc();

    List<Conversation> findByStatus(String status);
}
