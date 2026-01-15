package com.cyberstrike.controller;

import com.cyberstrike.dto.BatchQueueRequest;
import com.cyberstrike.entity.BatchQueue;
import com.cyberstrike.service.BatchTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    public List<BatchQueue> listQueues() {
        return batchTaskService.listQueues();
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
    public ResponseEntity<Void> startQueue(@PathVariable String id) {
        batchTaskService.processQueueAsync(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelQueue(@PathVariable String id) {
        batchTaskService.cancelQueue(id);
        return ResponseEntity.ok().build();
    }
}
