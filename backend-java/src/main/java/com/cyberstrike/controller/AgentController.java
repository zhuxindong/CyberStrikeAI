package com.cyberstrike.controller;

import com.cyberstrike.dto.ChatRequest;
import com.cyberstrike.dto.ChatResponse;
import com.cyberstrike.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    // POST /api/agent-loop - 同步调用
    @PostMapping("/agent-loop")
    public ResponseEntity<ChatResponse> agentLoop(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(agentService.agentLoop(request));
    }

    // POST /api/agent-loop/stream - 流式调用
    @PostMapping("/agent-loop/stream")
    public SseEmitter agentLoopStream(@RequestBody ChatRequest request) {
        return agentService.agentLoopStream(request);
    }

    // POST /api/agent-loop/cancel - 取消任务
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

    // GET /api/agent-loop/tasks - 列出运行中的任务
    @GetMapping("/agent-loop/tasks")
    public ResponseEntity<?> listAgentTasks() {
        List<Map<String, Object>> tasks = agentService.listRunningTasks();
        return ResponseEntity.ok(tasks);
    }

    // GET /api/agent-loop/completed - 列出已完成的任务
    @GetMapping("/agent-loop/completed")
    public ResponseEntity<?> listCompletedTasks() {
        List<Map<String, Object>> tasks = agentService.listCompletedTasks();
        return ResponseEntity.ok(tasks);
    }
}
