package com.cyberstrike.repository;

import com.cyberstrike.entity.AttackChainEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttackChainEdgeRepository extends JpaRepository<AttackChainEdge, String> {
    List<AttackChainEdge> findByConversationId(String conversationId);
}
