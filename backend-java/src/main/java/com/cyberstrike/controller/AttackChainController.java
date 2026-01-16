package com.cyberstrike.controller;

import com.cyberstrike.entity.AttackChainEdge;
import com.cyberstrike.entity.AttackChainNode;
import com.cyberstrike.repository.AttackChainEdgeRepository;
import com.cyberstrike.repository.AttackChainNodeRepository;
import com.cyberstrike.service.AttackChainService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/attack-chain")
public class AttackChainController {

    private final AttackChainNodeRepository nodeRepository;
    private final AttackChainEdgeRepository edgeRepository;
    private final AttackChainService attackChainService;

    public AttackChainController(
            AttackChainNodeRepository nodeRepository,
            AttackChainEdgeRepository edgeRepository,
            AttackChainService attackChainService) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.attackChainService = attackChainService;
    }

    /**
     * 获取攻击链（按需生成）
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getAttackChain(@PathVariable String conversationId) {
        try {
            Map<String, Object> result = attackChainService.getOrGenerateChain(conversationId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "生成攻击链失败: " + e.getMessage()));
        }
    }

    /**
     * 重新生成攻击链
     */
    @PostMapping("/{conversationId}/regenerate")
    public ResponseEntity<?> regenerateAttackChain(@PathVariable String conversationId) {
        try {
            Map<String, Object> result = attackChainService.regenerateChain(conversationId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "生成攻击链失败: " + e.getMessage()));
        }
    }

    /**
     * 手动添加节点
     */
    @PostMapping("/{conversationId}/nodes")
    public ResponseEntity<?> addNode(@PathVariable String conversationId,
            @RequestBody AttackChainNode node) {
        node.setId(UUID.randomUUID().toString());
        node.setConversationId(conversationId);
        node.setCreatedAt(java.time.LocalDateTime.now());
        nodeRepository.save(node);
        return ResponseEntity.ok(node);
    }

    /**
     * 手动添加边
     */
    @PostMapping("/{conversationId}/edges")
    public ResponseEntity<?> addEdge(@PathVariable String conversationId,
            @RequestBody AttackChainEdge edge) {
        edge.setId(UUID.randomUUID().toString());
        edge.setConversationId(conversationId);
        edge.setCreatedAt(java.time.LocalDateTime.now());
        edgeRepository.save(edge);
        return ResponseEntity.ok(edge);
    }

    /**
     * 清空攻击链
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<?> clearAttackChain(@PathVariable String conversationId) {
        attackChainService.deleteChain(conversationId);
        return ResponseEntity.ok(Map.of("message", "攻击链已清空"));
    }
}
