package com.cyberstrike.service;

import com.cyberstrike.dto.BatchQueueRequest;
import com.cyberstrike.entity.BatchQueue;
import com.cyberstrike.entity.BatchTask;
import com.cyberstrike.entity.Conversation;
import com.cyberstrike.repository.BatchQueueRepository;
import com.cyberstrike.repository.BatchTaskRepository;
import com.cyberstrike.repository.ConversationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BatchTaskService {
    private static final Logger log = LoggerFactory.getLogger(BatchTaskService.class);

    private final BatchQueueRepository queueRepository;
    private final BatchTaskRepository taskRepository;
    private final AgentService agentService;
    private final ConversationRepository conversationRepository;

    public BatchTaskService(BatchQueueRepository queueRepository,
            BatchTaskRepository taskRepository,
            AgentService agentService,
            ConversationRepository conversationRepository) {
        this.queueRepository = queueRepository;
        this.taskRepository = taskRepository;
        this.agentService = agentService;
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public BatchQueue createQueue(BatchQueueRequest request) {
        BatchQueue queue = new BatchQueue();
        queue.setId(UUID.randomUUID().toString());
        queue.setTitle(request.getTitle());
        queue.setStatus("pending");
        queue.setRole(request.getRole());
        queue.setCreatedAt(LocalDateTime.now());

        BatchQueue savedQueue = queueRepository.save(queue);
        if (request.getTasks() != null) {
            for (String msg : request.getTasks()) {
                BatchTask task = new BatchTask();
                task.setId(UUID.randomUUID().toString());
                task.setQueue(savedQueue);
                task.setMessage(msg);
                task.setStatus("pending");
                task.setCreatedAt(LocalDateTime.now());
                taskRepository.save(task);
            }
        }

        // Reload to get tasks
        return queueRepository.findById(savedQueue.getId()).orElse(savedQueue);
    }

    public List<BatchQueue> listQueues() {
        return queueRepository.findAllByOrderByCreatedAtDesc();
    }

    public Page<BatchQueue> findWithCriteria(String keyword, LocalDateTime createdFrom, LocalDateTime createdTo, String status, PageRequest pageable) {

        Specification<BatchQueue> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. 模糊查询: ID 或 标题 (使用 or 连接)
            if (keyword != null && !keyword.trim().isEmpty()) {
                Predicate idLike = criteriaBuilder.like(root.get("id"), "%" + keyword + "%");
                Predicate titleLike = criteriaBuilder.like(root.get("title"), "%" + keyword + "%");
                predicates.add(criteriaBuilder.or(idLike, titleLike));
            }

            // 2. 创建时间范围
            if (createdFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            // 3. 状态查询 (精确匹配)
            if (status != null && !status.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return queueRepository.findAll(spec, pageable);
    }

    public BatchQueue getQueue(String id) {
        return queueRepository.findById(id).orElse(null);
    }
    @Async("taskExecutor")
    public void processQueueAsync(String queueId) {
        log.info("Starting processing for queue: {}", queueId);
        BatchQueue queue = queueRepository.findById(queueId).orElse(null);
        if (queue == null)
            return;

        if ("running".equals(queue.getStatus())) {
            log.warn("Queue {} is already running", queueId);
            return;
        }

        queue.setStatus("running");
        if (queue.getStartedAt() == null) {
            queue.setStartedAt(LocalDateTime.now());
        }
        queueRepository.saveAndFlush(queue);

        List<BatchTask> tasks = queue.getTasks();
        // Simple sequential processing
        for (int i=queue.getCurrentIndex(); i < tasks.size(); i++) {
            BatchTask task = tasks.get(i);
            // Refresh Status check
            queue = queueRepository.findById(queueId).orElse(null);
            if (queue == null || "cancelled".equals(queue.getStatus())|| "paused".equals(queue.getStatus())) {
                log.info("Queue {} cancelled, stopping execution", queueId);
                break;
            }

            if ("completed".equals(task.getStatus()) || "error".equals(task.getStatus())) {
                continue; // Skip already processed
            }

            queue.setCurrentIndex(i);
            queueRepository.save(queue);

            processSingleTask(task);
        }

        // Check verification - if all done
        queue = queueRepository.findById(queueId).orElse(null);
        if (queue != null && !"cancelled".equals(queue.getStatus())) {
            boolean allCompleted = queue.getTasks().stream()
                    .allMatch(t -> "completed".equals(t.getStatus()) || "error".equals(t.getStatus()));
            if (allCompleted) {
                queue.setStatus("completed");
                queue.setCompletedAt(LocalDateTime.now());
                queueRepository.save(queue);
            }
        }
    }

    private void processSingleTask(BatchTask task) {
        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());

        // Create conversation
        Conversation conv = new Conversation();
        String msg = task.getMessage();
        conv.setTitle("Batch: " + (msg.length() > 20 ? msg.substring(0, 20) + "..." : msg));
        conv = conversationRepository.save(conv);
        task.setConversationId(conv.getId());
        taskRepository.save(task);
        try {
            // Include role context if set
            String fullMessage = task.getMessage();
            if (task.getQueue().getRole() != null && !task.getQueue().getRole().isEmpty()) {
                fullMessage = "Rule/Role: " + task.getQueue().getRole() + "\nTask: " + fullMessage;
            }

            String result = agentService.executeTaskSync(conv.getId(), fullMessage,task.getId());
            task.setResult(result);
            task.setStatus("completed");
        } catch (Exception e) {
            task.setError(e.getMessage());
            task.setStatus("error");
            log.error("Error processing batch task " + task.getId(), e);
        } finally {
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    public void cancelQueue(String id) {
        BatchQueue queue = queueRepository.findById(id).orElse(null);
        if (queue != null) {
            queue.setStatus("paused");
            queueRepository.save(queue);
        }
        for (BatchTask task : queue.getTasks()){
            if ("running".equals(task.getStatus())){
                agentService.cancelTask(task.getId());
            }
        }
    }
    @Transactional
    public ResponseEntity<?> deleteQueue(String id) {
        if (queueRepository.existsById(id)) {
            BatchQueue queue=queueRepository.findById(id).get();
            taskRepository.deleteAll(queue.getTasks());
            queueRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }
    @Transactional
    public ResponseEntity<?> deleteTask(String id) {
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    public ResponseEntity<?> updateTask(String id, BatchTask batchTask) {
        if (taskRepository.existsById(id)) {
            BatchTask existingTask = taskRepository.findById(id).get();
            if (batchTask.getMessage() != null) {
                existingTask.setMessage(batchTask.getMessage());
            }
            // 3. 保存并返回
            return ResponseEntity.ok().body(taskRepository.save(existingTask));
        }
        return ResponseEntity.notFound().build();

    }

    public ResponseEntity<?> createTask(BatchTask batchTask) {
        batchTask.setId(UUID.randomUUID().toString());
        batchTask.setCreatedAt(LocalDateTime.now());
        batchTask.setStatus("pending");
        return ResponseEntity.ok().body(taskRepository.save(batchTask));
    }

    public ResponseEntity<Map<String, Integer>> status() {
        List<BatchQueue> batchQueueList = queueRepository.findAll();
        Map<String, Integer> statusCounts = batchQueueList.stream()
                .collect(Collectors.groupingBy(
                        BatchQueue::getStatus,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
        statusCounts.put("total", batchQueueList.size());
        return ResponseEntity.ok(statusCounts);
    }
}
