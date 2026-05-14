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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/multi-agent")
@RequiredArgsConstructor
@Tag(name = "Multi-Agent Management", description = "多代理管理接口")
public class AgentFileController {

    private final AgentFileService agentFileService;

    @GetMapping("/markdown-agents")
    @Operation(summary = "获取 Agent 列表")
    public ResponseEntity<?> listMarkdownAgents() {
        try {
            AgentListResponse response = agentFileService.listAgentsForUI();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取 Agent 列表失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/markdown-agents/{filename}")
    @Operation(summary = "获取单个 Agent")
    public ResponseEntity<?> getMarkdownAgent(@PathVariable String filename) {
        try {
            AgentMetadata agent = agentFileService.getAgent(filename);
            return ResponseEntity.ok(agent);
        } catch (RuntimeException e) {
            log.error("获取 Agent 失败: {}", filename, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取 Agent 失败: {}", filename, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/markdown-agents")
    @Operation(summary = "创建 Agent")
    public ResponseEntity<?> createMarkdownAgent(@RequestBody AgentMetadata request) {
        try {
            String filename = request.getFilename();
            if (filename == null || filename.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "文件名不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            if (!filename.matches("^[a-zA-Z0-9._-]+\\.md$")) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "文件名格式不正确，只能包含字母、数字、点、下划线、连字符，且必须以 .md 结尾");
                return ResponseEntity.badRequest().body(error);
            }

            if (request.getName() == null || request.getName().trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Agent 名称不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            if (request.getInstruction() == null || request.getInstruction().trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Agent 指令不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            AgentMetadata created = agentFileService.createAgent(filename, request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (RuntimeException e) {
            log.error("创建 Agent 失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("创建 Agent 失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PutMapping("/markdown-agents/{filename}")
    @Operation(summary = "更新 Agent")
    public ResponseEntity<?> updateMarkdownAgent(
            @PathVariable String filename,
            @RequestBody AgentMetadata request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Agent 名称不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            if (request.getInstruction() == null || request.getInstruction().trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Agent 指令不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            AgentMetadata updated = agentFileService.updateAgent(filename, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (RuntimeException e) {
            log.error("更新 Agent 失败: {}", filename, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("更新 Agent 失败: {}", filename, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @DeleteMapping("/markdown-agents/{filename:.+}")
    @Operation(summary = "删除 Agent")
    public ResponseEntity<?> deleteMarkdownAgent(@PathVariable String filename) {
        try {
            boolean deleted = agentFileService.deleteAgent(filename);
            if (!deleted) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Agent 不存在");
                return ResponseEntity.notFound().build();
            }
            Map<String, String> success = new HashMap<>();
            success.put("message", "删除成功");
            return ResponseEntity.ok(success);
        } catch (RuntimeException e) {
            log.error("删除 Agent 失败: {}", filename, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("不能删除主代理")) {
                return ResponseEntity.badRequest().body(error);
            }
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("删除 Agent 失败: {}", filename, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}