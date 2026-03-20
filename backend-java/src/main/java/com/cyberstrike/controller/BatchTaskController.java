package com.cyberstrike.controller;

import com.cyberstrike.dto.BatchQueueRequest;
import com.cyberstrike.entity.BatchQueue;
import com.cyberstrike.entity.BatchTask;
import com.cyberstrike.service.BatchTaskService;
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
public class BatchTaskController {

    private final BatchTaskService batchTaskService;

    public BatchTaskController(BatchTaskService batchTaskService) {
        this.batchTaskService = batchTaskService;
    }

    @PostMapping
    public ResponseEntity<BatchQueue> createQueue(@RequestBody BatchQueueRequest request) {
        return ResponseEntity.ok(batchTaskService.createQueue(request));
    }

//    @GetMapping
//    public List<BatchQueue> listQueues() {
//        return batchTaskService.listQueues();
//    }

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

    @GetMapping("/{id}")
    public ResponseEntity<BatchQueue> getQueue(@PathVariable String id) {
        BatchQueue queue = batchTaskService.getQueue(id);
        if (queue == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(queue);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> startQueue(@PathVariable String id) {
        batchTaskService.processQueueAsync(id);
        return ResponseEntity.ok("执行成功");
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelQueue(@PathVariable String id) {
        batchTaskService.cancelQueue(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQueue(@PathVariable String id) {
        return batchTaskService.deleteQueue(id);
    }

    @DeleteMapping("/task/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable String id) {
        return batchTaskService.deleteTask(id);
    }

    @PutMapping("/task/{id}")
    public ResponseEntity<?> updateTask(@PathVariable String id,@RequestBody BatchTask batchTask) {
        return batchTaskService.updateTask(id,batchTask);
    }

    @PostMapping("/task")
    public ResponseEntity<?> createTask(@RequestBody BatchTask batchTask) {
        return batchTaskService.createTask(batchTask);
    }

    @GetMapping ("/staus")
    public ResponseEntity<Map<String,Integer>> status() {
        return batchTaskService.status();
    }
}
