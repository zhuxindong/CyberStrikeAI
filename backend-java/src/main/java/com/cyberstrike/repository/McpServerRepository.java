package com.cyberstrike.repository;

import com.cyberstrike.entity.McpServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface McpServerRepository extends JpaRepository<McpServer, String> {
    List<McpServer> findAllByOrderByCreatedAtDesc();

    List<McpServer> findByStatus(String status);

    Optional<McpServer> findByName(String name);

    List<McpServer> findByEnabled(String enable);

}
