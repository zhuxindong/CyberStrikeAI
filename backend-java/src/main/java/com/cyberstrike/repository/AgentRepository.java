package com.cyberstrike.repository;

import com.cyberstrike.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, Integer> {
    Optional<AgentEntity> findByFilename(String filename);
    boolean existsByFilename(String filename);
    List<AgentEntity> findByKind(String kind);
    List<AgentEntity> findByIsOrchestrator(boolean isOrchestrator);
    List<AgentEntity> findByBindRole(String bindRole);
    Optional<AgentEntity> findByAgentId(String agentId);
}