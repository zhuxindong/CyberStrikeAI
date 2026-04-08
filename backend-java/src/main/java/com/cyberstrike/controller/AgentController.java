package com.cyberstrike.controller;

import com.cyberstrike.dto.ChatRequest;
import com.cyberstrike.dto.ChatResponse;
import com.cyberstrike.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@Tag(name = "Agent", description = "智能体对话与任务编排接口")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "同步执行智能体对话/工具链", description = "POST /api/agent-loop")
    @PostMapping("/agent-loop")
    public ResponseEntity<ChatResponse> agentLoop(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(agentService.agentLoop(request));
    }

    @Operation(summary = "流式执行智能体对话/工具链 (SSE)", description = "POST /api/agent-loop/stream")
    @PostMapping("/agent-loop/stream")
    public SseEmitter agentLoopStream(@RequestBody ChatRequest request) {
        return agentService.agentLoopStream(request);
    }

    @Operation(summary = "取消智能体任务", description = "POST /api/agent-loop/cancel 通过 task_id 取消任务")
    @PostMapping("/agent-loop/cancel")
    public ResponseEntity<?> cancelAgentLoop(@RequestBody Map<String, String> body) {
        String taskId = body.get("task_id");
        if (taskId == null || taskId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "task_id 不能为空"));
        }
        boolean cancelled = agentService.cancelTask(taskId);
        if (cancelled) {
            return ResponseEntity.ok(Map.of("message", "任务已取消"));
        }
        return ResponseEntity.ok(Map.of("message", "任务不存在或已完成"));
    }

    @Operation(summary = "列出运行中的智能体任务", description = "GET /api/agent-loop/tasks")
    @GetMapping("/agent-loop/tasks")
    public ResponseEntity<?> listAgentTasks() {
        List<Map<String, Object>> tasks = agentService.listRunningTasks();
        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "列出已完成的智能体任务", description = "GET /api/agent-loop/completed")
    @GetMapping("/agent-loop/completed")
    public ResponseEntity<?> listCompletedTasks() {
        List<Map<String, Object>> tasks = agentService.listCompletedTasks();
        return ResponseEntity.ok(tasks);
    }
}
