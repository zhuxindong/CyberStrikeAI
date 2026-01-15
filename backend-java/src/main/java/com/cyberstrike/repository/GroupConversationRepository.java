package com.cyberstrike.repository;

import com.cyberstrike.entity.GroupConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupConversationRepository extends JpaRepository<GroupConversation, Long> {

    List<GroupConversation> findByGroupId(String groupId);

    Optional<GroupConversation> findByGroupIdAndConversationId(String groupId, String conversationId);

    @Transactional
    void deleteByGroupIdAndConversationId(String groupId, String conversationId);

    @Transactional
    void deleteByGroupId(String groupId);

    boolean existsByGroupIdAndConversationId(String groupId, String conversationId);
}
