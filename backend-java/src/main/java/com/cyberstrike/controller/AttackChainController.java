package com.cyberstrike.controller;

import com.cyberstrike.entity.AttackChainEdge;
import com.cyberstrike.entity.AttackChainNode;
import com.cyberstrike.repository.AttackChainEdgeRepository;
import com.cyberstrike.repository.AttackChainNodeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/attack-chain")
public class AttackChainController {

    private final AttackChainNodeRepository nodeRepository;
    private final AttackChainEdgeRepository edgeRepository;

    public AttackChainController(AttackChainNodeRepository nodeRepository,
            AttackChainEdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getAttackChain(@PathVariable String conversationId) {
        List<AttackChainNode> nodes = nodeRepository.findByConversationId(conversationId);
        List<AttackChainEdge> edges = edgeRepository.findByConversationId(conversationId);

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{conversationId}/nodes")
    public ResponseEntity<?> addNode(@PathVariable String conversationId,
            @RequestBody AttackChainNode node) {
        node.setId(UUID.randomUUID().toString());
        node.setConversationId(conversationId);
        node.setCreatedAt(java.time.LocalDateTime.now());
        nodeRepository.save(node);
        return ResponseEntity.ok(node);
    }

    @PostMapping("/{conversationId}/edges")
    public ResponseEntity<?> addEdge(@PathVariable String conversationId,
            @RequestBody AttackChainEdge edge) {
        edge.setId(UUID.randomUUID().toString());
        edge.setConversationId(conversationId);
        edge.setCreatedAt(java.time.LocalDateTime.now());
        edgeRepository.save(edge);
        return ResponseEntity.ok(edge);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<?> clearAttackChain(@PathVariable String conversationId) {
        List<AttackChainNode> nodes = nodeRepository.findByConversationId(conversationId);
        List<AttackChainEdge> edges = edgeRepository.findByConversationId(conversationId);
        nodeRepository.deleteAll(nodes);
        edgeRepository.deleteAll(edges);
        return ResponseEntity.ok(Map.of("message", "攻击链已清空"));
    }
}
