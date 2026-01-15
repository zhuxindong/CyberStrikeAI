package com.cyberstrike.repository;

import com.cyberstrike.entity.AttackChainNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttackChainNodeRepository extends JpaRepository<AttackChainNode, String> {
    List<AttackChainNode> findByConversationId(String conversationId);
}
