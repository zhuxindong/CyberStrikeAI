package com.cyberstrike.controller;

import com.cyberstrike.dto.AgentListResponse;
import com.cyberstrike.entity.AgentMetadata;
import com.cyberstrike.service.AgentFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/multi-agent")
@RequiredArgsConstructor
@Tag(name = "Multi-Agent Management", description = "多代理管理接口")
public class AgentFileController {

    private final AgentFileService agentFileService;

    @GetMapping("/markdown-agents")
    @Operation(summary = "获取 Agent 列表")
    public ResponseEntity<AgentListResponse> listMarkdownAgents() {
        try {
            AgentListResponse response = agentFileService.listAgentsForUI();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取 Agent 列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/markdown-agents/{filename}")
    @Operation(summary = "获取单个 Agent")
    public ResponseEntity<AgentMetadata> getMarkdownAgent(@PathVariable String filename) {
        try {
            AgentMetadata agent = agentFileService.getAgent(filename);
            return ResponseEntity.ok(agent);
        } catch (IOException e) {
            log.error("获取 Agent 失败: {}", filename, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取 Agent 失败: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/markdown-agents")
    @Operation(summary = "创建 Agent")
    public ResponseEntity<AgentMetadata> createMarkdownAgent(@RequestBody AgentMetadata request) {
        try {
            String filename = request.getFilename();
            if (filename == null || filename.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            if (!filename.matches("^[a-zA-Z0-9._-]+\\.md$")) {
                return ResponseEntity.badRequest().build();
            }

            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            if (request.getInstruction() == null || request.getInstruction().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            AgentMetadata created = agentFileService.createAgent(filename, request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("创建 Agent 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/markdown-agents/{filename}")
    @Operation(summary = "更新 Agent")
    public ResponseEntity<AgentMetadata> updateMarkdownAgent(
            @PathVariable String filename,
            @RequestBody AgentMetadata request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            if (request.getInstruction() == null || request.getInstruction().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            AgentMetadata updated = agentFileService.updateAgent(filename, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("更新 Agent 失败: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/markdown-agents/{filename:.+}")
    @Operation(summary = "删除 Agent")
    public ResponseEntity<Void> deleteMarkdownAgent(@PathVariable String filename) {
        try {
            boolean deleted = agentFileService.deleteAgent(filename);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("删除 Agent 失败: {}", filename, e);
            if (e.getMessage() != null && e.getMessage().contains("不能删除主代理")) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }
}