package com.cyberstrike.controller;

import com.cyberstrike.entity.ToolExecution;
import com.cyberstrike.repository.ToolExecutionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/monitor")
@Tag(name = "Monitor", description = "工具执行监控与统计接口")
public class MonitorController {

    private final ToolExecutionRepository toolExecutionRepository;

    public MonitorController(ToolExecutionRepository toolExecutionRepository) {
        this.toolExecutionRepository = toolExecutionRepository;
    }

    @Operation(summary = "分页查询工具执行记录", description = "GET /api/monitor 支持 limit/offset")
    @GetMapping
    public ResponseEntity<?> getExecutions(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        List<ToolExecution> executions = toolExecutionRepository.findAllByOrderByCreatedAtDesc();
        int end = Math.min(offset + limit, executions.size());
        List<ToolExecution> page = (offset < executions.size())
                ? executions.subList(offset, end)
                : List.of();
        return ResponseEntity.ok(page);
    }

    @Operation(summary = "获取单条执行记录", description = "GET /api/monitor/execution/{id}")
    @GetMapping("/execution/{id}")
    public ResponseEntity<?> getExecution(@Parameter(description = "执行记录ID") @PathVariable String id) {
        return toolExecutionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取执行统计", description = "GET /api/monitor/stats 包含工具调用与状态统计")
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 工具使用统计
        List<Map<String, Object>> toolStats = new ArrayList<>();
        for (Object[] row : toolExecutionRepository.getToolStats()) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("toolName", row[0]);
            stat.put("count", row[1]);
            stat.put("avgDurationMs", row[2]);
            toolStats.add(stat);
        }
        stats.put("toolStats", toolStats);

        // 状态统计
        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] row : toolExecutionRepository.countByStatus()) {
            statusCounts.put((String) row[0], (Long) row[1]);
        }
        stats.put("statusCounts", statusCounts);

        stats.put("totalExecutions", toolExecutionRepository.count());

        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "删除单条执行记录", description = "DELETE /api/monitor/execution/{id}")
    @DeleteMapping("/execution/{id}")
    public ResponseEntity<?> deleteExecution(@Parameter(description = "执行记录ID") @PathVariable String id) {
        if (toolExecutionRepository.existsById(id)) {
            toolExecutionRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(summary = "清空所有执行记录", description = "DELETE /api/monitor/executions")
    @DeleteMapping("/executions")
    public ResponseEntity<?> clearExecutions() {
        toolExecutionRepository.deleteAll();
        return ResponseEntity.ok(Map.of("message", "已清空所有记录"));
    }
}
