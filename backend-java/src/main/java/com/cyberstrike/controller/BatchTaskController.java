package com.cyberstrike.controller;

import com.cyberstrike.dto.BatchQueueRequest;
import com.cyberstrike.entity.BatchQueue;
import com.cyberstrike.entity.BatchTask;
import com.cyberstrike.service.BatchTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch-tasks")
@Tag(name = "BatchTask", description = "批量任务队列与任务管理接口")
public class BatchTaskController {

    private final BatchTaskService batchTaskService;

    public BatchTaskController(BatchTaskService batchTaskService) {
        this.batchTaskService = batchTaskService;
    }

    @Operation(summary = "创建任务队列", description = "POST /api/batch-tasks")
    @PostMapping
    public ResponseEntity<BatchQueue> createQueue(@RequestBody BatchQueueRequest request) {
        return ResponseEntity.ok(batchTaskService.createQueue(request));
    }

//    @GetMapping
//    public List<BatchQueue> listQueues() {
//        return batchTaskService.listQueues();
//    }

    @Operation(summary = "分页查询任务队列", description = "GET /api/batch-tasks 支持关键词、时间范围、状态过滤")
    @GetMapping
    public ResponseEntity<?> listQueues(
            // 分页参数
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,

            // 新增的查询参数
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")  LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")  LocalDateTime createdTo,
            @RequestParam(required = false) String status) {

        Page<BatchQueue> queuePage = batchTaskService.findWithCriteria(keyword, createdFrom, createdTo, status, PageRequest.of(page - 1, size, Sort.Direction.DESC, "createdAt"));

        Map<String, Object> response = new HashMap<>();
        response.put("data", queuePage.getContent());
        response.put("total", queuePage.getTotalElements());
        response.put("currentPage", queuePage.getNumber() + 1);
        response.put("pageSize", queuePage.getSize());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "获取队列详情", description = "GET /api/batch-tasks/{id}")
    @GetMapping("/{id}")
    public ResponseEntity<BatchQueue> getQueue(@Parameter(description = "队列ID") @PathVariable String id) {
        BatchQueue queue = batchTaskService.getQueue(id);
        if (queue == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(queue);
    }

    @Operation(summary = "启动队列执行", description = "POST /api/batch-tasks/{id}/start")
    @PostMapping("/{id}/start")
    public ResponseEntity<?> startQueue(@Parameter(description = "队列ID") @PathVariable String id) {
        batchTaskService.processQueueAsync(id);
        return ResponseEntity.ok("执行成功");
    }

    @Operation(summary = "取消队列", description = "POST /api/batch-tasks/{id}/cancel")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelQueue(@Parameter(description = "队列ID") @PathVariable String id) {
        batchTaskService.cancelQueue(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "删除队列", description = "DELETE /api/batch-tasks/{id}")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQueue(@Parameter(description = "队列ID") @PathVariable String id) {
        return batchTaskService.deleteQueue(id);
    }

    @Operation(summary = "删除任务", description = "DELETE /api/batch-tasks/task/{id}")
    @DeleteMapping("/task/{id}")
    public ResponseEntity<?> deleteTask(@Parameter(description = "任务ID") @PathVariable String id) {
        return batchTaskService.deleteTask(id);
    }

    @Operation(summary = "更新任务", description = "PUT /api/batch-tasks/task/{id}")
    @PutMapping("/task/{id}")
    public ResponseEntity<?> updateTask(@Parameter(description = "任务ID") @PathVariable String id,@RequestBody BatchTask batchTask) {
        return batchTaskService.updateTask(id,batchTask);
    }

    @Operation(summary = "创建任务", description = "POST /api/batch-tasks/task")
    @PostMapping("/task")
    public ResponseEntity<?> createTask(@RequestBody BatchTask batchTask) {
        return batchTaskService.createTask(batchTask);
    }

    @Operation(summary = "获取队列状态统计", description = "GET /api/batch-tasks/staus")
    @GetMapping ("/staus")
    public ResponseEntity<Map<String,Integer>> status() {
        return batchTaskService.status();
    }
}
