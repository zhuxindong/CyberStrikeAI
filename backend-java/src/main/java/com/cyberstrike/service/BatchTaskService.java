package com.cyberstrike.service;

import com.cyberstrike.dto.BatchQueueRequest;
import com.cyberstrike.entity.BatchQueue;
import com.cyberstrike.entity.BatchTask;
import com.cyberstrike.entity.Conversation;
import com.cyberstrike.repository.BatchQueueRepository;
import com.cyberstrike.repository.BatchTaskRepository;
import com.cyberstrike.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
                taskRepository.save(task);
            }
        }

        // Reload to get tasks
        return queueRepository.findById(savedQueue.getId()).orElse(savedQueue);
    }

    public List<BatchQueue> listQueues() {
        return queueRepository.findAllByOrderByCreatedAtDesc();
    }

    public BatchQueue getQueue(String id) {
        return queueRepository.findById(id).orElse(null);
    }

    @Async
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
        queueRepository.save(queue);

        List<BatchTask> tasks = queue.getTasks();
        // Simple sequential processing
        for (int i = 0; i < tasks.size(); i++) {
            BatchTask task = tasks.get(i);

            // Refresh Status check
            queue = queueRepository.findById(queueId).orElse(null);
            if (queue == null || "cancelled".equals(queue.getStatus())) {
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

            String result = agentService.executeTaskSync(conv.getId(), fullMessage);

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
            queue.setStatus("cancelled");
            queueRepository.save(queue);
        }
    }
}
