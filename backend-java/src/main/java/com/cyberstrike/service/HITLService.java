package com.cyberstrike.service;

import com.cyberstrike.dto.HITLRequest;
import com.cyberstrike.entity.HITLConversationConfig;
import com.cyberstrike.entity.HITLInterrupt;
import com.cyberstrike.repository.HITLConversationConfigRepository;
import com.cyberstrike.repository.HITLInterruptRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HITL (Human-in-the-Loop) 人机协同服务
 * 对应 Go 版本 internal/handler/hitl.go 的 HITLManager
 */
@Service
public class HITLService {

    private static final Logger log = LoggerFactory.getLogger(HITLService.class);

    private final HITLInterruptRepository interruptRepository;
    private final HITLConversationConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    // 运行时配置（内存）
    private final Map<String, HITLRuntimeConfig> runtimeConfigs = new ConcurrentHashMap<>();

    // 待审批中断（内存）
    private final Map<String, PendingInterrupt> pendingInterrupts = new ConcurrentHashMap<>();

    // 全局工具白名单（来自 config.yaml）
    private volatile List<String> globalToolWhitelist = Collections.emptyList();

    // 事件回调（用于 SSE 推送）
    private volatile HITLEventCallback eventCallback;

    public HITLService(HITLInterruptRepository interruptRepository,
                       HITLConversationConfigRepository configRepository,
                       ObjectMapper objectMapper) {
        this.interruptRepository = interruptRepository;
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;

        // 启动时清理孤儿中断
        cleanupOrphanedInterrupts();
    }

    public void setEventCallback(HITLEventCallback callback) {
        this.eventCallback = callback;
    }

    public void setGlobalToolWhitelist(List<String> whitelist) {
        this.globalToolWhitelist = whitelist != null ? new ArrayList<>(whitelist) : Collections.emptyList();
    }

    // ==================== 初始化与清理 ====================

    private void cleanupOrphanedInterrupts() {
        try {
            int count = interruptRepository.cancelAllPending("process restarted", LocalDateTime.now());
            if (count > 0) {
                log.info("cancelled orphaned HITL interrupts from previous process, count={}", count);
            }
        } catch (Exception e) {
            log.warn("failed to cancel orphaned HITL interrupts", e);
        }
    }

    // ==================== 会话配置管理 ====================

    /**
     * 激活会话的 HITL 配置
     */
    public void activateConversation(String conversationId, HITLRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getEnabled())) {
            deactivateConversation(conversationId);
            return;
        }

        List<String> tools = request.getSensitiveTools() != null ? request.getSensitiveTools() : Collections.emptyList();
        Set<String> toolSet = new HashSet<>();
        for (String t : tools) {
            if (t != null && !t.trim().isEmpty()) {
                toolSet.add(t.trim().toLowerCase());
            }
        }

        Duration timeout = Duration.ZERO;
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() > 0) {
            timeout = Duration.ofSeconds(request.getTimeoutSeconds());
        }

        HITLRuntimeConfig config = new HITLRuntimeConfig();
        config.setEnabled(true);
        config.setMode(normalizeMode(request.getMode()));
        config.setSensitiveTools(toolSet);
        config.setTimeout(timeout);

        runtimeConfigs.put(conversationId, config);
    }

    /**
     * 停用会话的 HITL 配置
     */
    public void deactivateConversation(String conversationId) {
        runtimeConfigs.remove(conversationId);
    }

    /**
     * 判断是否需要拦截工具调用
     */
    public boolean needsToolApproval(String conversationId, String toolName) {
        HITLRuntimeConfig config = runtimeConfigs.get(conversationId);
        if (config == null || !config.isEnabled()) {
            return false;
        }

        // 空白名单 => 全部工具都需要审批
        if (config.getSensitiveTools().isEmpty()) {
            return true;
        }

        // 工具在白名单中 => 不需要审批
        return !config.getSensitiveTools().contains(toolName.trim().toLowerCase());
    }

    /**
     * 获取会话的 HITL 配置
     */
    public HITLRequest getConversationConfig(String conversationId) {
        HITLConversationConfig entity = configRepository.findByConversationId(conversationId).orElse(null);
        if (entity == null) {
            return new HITLRequest(false, "off", Collections.emptyList(), 0);
        }

        List<String> tools = new ArrayList<>();
        try {
            tools = objectMapper.readValue(entity.getSensitiveTools(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse sensitiveTools for conversation: {}", conversationId, e);
        }

        return new HITLRequest(
                entity.getEnabled(),
                entity.getMode(),
                tools,
                entity.getTimeoutSeconds()
        );
    }

    /**
     * 保存会话 HITL 配置
     */
    @Transactional
    public void saveConversationConfig(String conversationId, HITLRequest request) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new IllegalArgumentException("conversationId is required");
        }

        if (request == null) {
            request = new HITLRequest(false, "off", Collections.emptyList(), 0);
        }

        String mode = normalizeMode(request.getMode());
        if (!Boolean.TRUE.equals(request.getEnabled())) {
            mode = "off";
        }

        String toolsJson = "[]";
        try {
            toolsJson = objectMapper.writeValueAsString(request.getSensitiveTools() != null ? request.getSensitiveTools() : Collections.emptyList());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize sensitiveTools", e);
        }

        int timeout = request.getTimeoutSeconds() != null ? Math.max(0, request.getTimeoutSeconds()) : 0;

        // 使用 upsert 方式
        HITLConversationConfig config = configRepository.findByConversationId(conversationId).orElse(null);
        if (config == null) {
            config = new HITLConversationConfig();
            config.setConversationId(conversationId);
            config.setEnabled(request.getEnabled());
            config.setMode(mode);
            config.setSensitiveTools(toolsJson);
            config.setTimeoutSeconds(timeout);
            configRepository.save(config);
        } else {
            config.setEnabled(request.getEnabled());
            config.setMode(mode);
            config.setSensitiveTools(toolsJson);
            config.setTimeoutSeconds(timeout);
            config.setUpdatedAt(LocalDateTime.now());
            configRepository.save(config);
        }
    }

    // ==================== 中断管理 ====================

    /**
     * 创建待审批中断
     */
    @Transactional
    public PendingInterrupt createPendingInterrupt(String conversationId, String assistantMessageId,
                                                   String mode, String toolName, String toolCallId,
                                                   Map<String, Object> payload) {
        String interruptId = "hitl_" + UUID.randomUUID().toString().replace("-", "");
        String modeNormalized = normalizeMode(mode);

        String payloadJson = "{}";
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize payload for interrupt: {}", interruptId, e);
        }

        HITLInterrupt interrupt = new HITLInterrupt();
        interrupt.setId(interruptId);
        interrupt.setConversationId(conversationId);
        interrupt.setMessageId(assistantMessageId);
        interrupt.setMode(modeNormalized);
        interrupt.setToolName(toolName);
        interrupt.setToolCallId(toolCallId);
        interrupt.setPayload(payloadJson);
        interrupt.setStatus("pending");
        interruptRepository.save(interrupt);

        // 确保会话配置已持久化
        ensureConversationHITLModePersisted(conversationId, modeNormalized);

        PendingInterrupt pending = new PendingInterrupt();
        pending.setInterruptId(interruptId);
        pending.setConversationId(conversationId);
        pending.setMode(modeNormalized);
        pending.setToolName(toolName);
        pending.setToolCallId(toolCallId);
        pending.setDecisionChannel(new LinkedBlockingQueue<>(1));

        pendingInterrupts.put(interruptId, pending);

        return pending;
    }

    /**
     * 等待审批决策
     */
    public HITLDecision waitDecision(String interruptId, Duration timeout) throws InterruptedException, TimeoutException {
        PendingInterrupt pending = pendingInterrupts.get(interruptId);
        if (pending == null) {
            throw new IllegalStateException("interrupt not found or already resolved");
        }

        try {
            HITLDecision decision;
            if (timeout == null || timeout.toMillis() <= 0) {
                // timeout 小于等于 0 表示没有超时时间，持续等待人工决策
                decision = pending.getDecisionChannel().take();
            } else {
                decision = pending.getDecisionChannel().poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }

            if (decision == null) {
                // 超时：自动批准（普通模式）
                updateInterruptStatus(interruptId, "timeout", "approve", "timeout auto approve", LocalDateTime.now());
                return new HITLDecision("approve", "timeout auto approve", Collections.emptyMap());
            }
            return decision;
        } finally {
            pendingInterrupts.remove(interruptId);
        }
    }

    /**
     * 取消审批（任务取消时）
     */
    public void cancelDecision(String interruptId) {
        PendingInterrupt pending = pendingInterrupts.remove(interruptId);
        if (pending != null) {
            pending.getDecisionChannel().clear();
        }
        updateInterruptStatus(interruptId, "cancelled", "reject", "task cancelled", LocalDateTime.now());
    }

    /**
     * 解决审批（人工决策）
     */
    public void resolveInterrupt(String interruptId, String decision, String comment, Map<String, Object> editedArguments) {
        if (!"approve".equalsIgnoreCase(decision) && !"reject".equalsIgnoreCase(decision)) {
            throw new IllegalArgumentException("decision must be approve/reject");
        }

        PendingInterrupt pending = pendingInterrupts.get(interruptId);
        if (pending == null) {
            throw new IllegalStateException("interrupt not found or already resolved");
        }

        HITLDecision d = new HITLDecision(decision.toLowerCase(), comment, editedArguments);
        boolean offered = pending.getDecisionChannel().offer(d);
        if (!offered) {
            throw new IllegalStateException("interrupt already resolved or decision channel busy");
        }

        updateInterruptStatus(interruptId, "decided", decision.toLowerCase(), comment, LocalDateTime.now());
    }

    /**
     * 撤销/忽略审批
     */
    public void dismissInterrupt(String interruptId) {
        PendingInterrupt pending = pendingInterrupts.remove(interruptId);
        if (pending != null) {
            pending.getDecisionChannel().clear();
            pending.getDecisionChannel().offer(new HITLDecision("reject", "dismissed by user", Collections.emptyMap()));
        }
        updateInterruptStatus(interruptId, "cancelled", "reject", "dismissed by user", LocalDateTime.now());
    }

    // ==================== 查询 ====================

    /**
     * 列出待审批中断
     */
    public List<HITLInterrupt> listPendingInterrupts(String conversationId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page-1, pageSize);

        // 如果 conversationId 为空或空白，查询所有会话的 pending 中断
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return interruptRepository.findByStatus("pending", pageable).getContent();
        } else {
            return interruptRepository.findByConversationIdAndStatusOrderByCreatedAtDesc(
                    conversationId, "pending", pageable
            ).getContent();
        }
    }

    /**
     * 获取待审批中断模式（用于前端配置对齐）
     */
    public Optional<String> getPendingInterruptMode(String conversationId) {
        return interruptRepository.findTopByConversationIdAndStatusOrderByCreatedAtDesc(conversationId, "pending")
                .map(HITLInterrupt::getMode);
    }

    // ==================== 工具白名单 ====================

    /**
     * 获取合并后的工具白名单（全局 + 会话级）
     */
    public Set<String> getMergedToolWhitelist(HITLRequest request) {
        Set<String> merged = new HashSet<>();
        if (globalToolWhitelist != null) {
            for (String tool : globalToolWhitelist) {
                if (tool != null && !tool.trim().isEmpty()) {
                    merged.add(tool.trim().toLowerCase());
                }
            }
        }
        if (request.getSensitiveTools() != null) {
            for (String tool : request.getSensitiveTools()) {
                if (tool != null && !tool.trim().isEmpty()) {
                    merged.add(tool.trim().toLowerCase());
                }
            }
        }
        return merged;
    }

    /**
     * 获取全局工具白名单
     */
    public List<String> getGlobalToolWhitelist() {
        return globalToolWhitelist != null ? new ArrayList<>(globalToolWhitelist) : Collections.emptyList();
    }

    /**
     * 获取会话的运行时配置（用于 AgentService 集成）
     */
    public HITLRuntimeConfig getRuntimeConfig(String conversationId) {
        return runtimeConfigs.get(conversationId);
    }

    // ==================== 内部方法 ====================

    private void ensureConversationHITLModePersisted(String conversationId, String mode) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return;
        }
        String normalizedMode = normalizeMode(mode);
        if ("off".equals(normalizedMode)) {
            return;
        }

        HITLConversationConfig config = configRepository.findByConversationId(conversationId).orElse(null);
        if (config != null && Boolean.TRUE.equals(config.getEnabled()) && normalizedMode.equals(config.getMode())) {
            return;
        }

        if (config == null) {
            config = new HITLConversationConfig();
            config.setConversationId(conversationId);
            config.setEnabled(true);
            config.setMode(normalizedMode);
            config.setSensitiveTools("[]");
            config.setTimeoutSeconds(0);
            configRepository.save(config);
        } else {
            config.setEnabled(true);
            config.setMode(normalizedMode);
            config.setUpdatedAt(LocalDateTime.now());
            configRepository.save(config);
        }
    }

    private void updateInterruptStatus(String interruptId, String status, String decision, String comment, LocalDateTime decidedAt) {
        try {
            interruptRepository.updateStatus(interruptId, status, decision, comment, decidedAt);
        } catch (Exception e) {
            log.warn("Failed to update interrupt status: {}", interruptId, e);
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return "approval";
        }
        String v = mode.trim().toLowerCase();
        return switch (v) {
            case "off" -> "off";
            case "feedback", "followup" -> "approval";
            case "approval", "review_edit" -> v;
            default -> "approval";
        };
    }

    // ==================== 内部类 ====================

    public static class HITLRuntimeConfig {
        private boolean enabled;
        private String mode;
        private Set<String> sensitiveTools = Collections.emptySet();
        private Duration timeout = Duration.ZERO;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Set<String> getSensitiveTools() { return sensitiveTools; }
        public void setSensitiveTools(Set<String> sensitiveTools) { this.sensitiveTools = sensitiveTools; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    public static class PendingInterrupt {
        private String interruptId;
        private String conversationId;
        private String mode;
        private String toolName;
        private String toolCallId;
        private BlockingQueue<HITLDecision> decisionChannel;

        public String getInterruptId() { return interruptId; }
        public void setInterruptId(String interruptId) { this.interruptId = interruptId; }
        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
        public BlockingQueue<HITLDecision> getDecisionChannel() { return decisionChannel; }
        public void setDecisionChannel(BlockingQueue<HITLDecision> decisionChannel) { this.decisionChannel = decisionChannel; }
    }

    public static class HITLDecision {
        private String decision;
        private String comment;
        private Map<String, Object> editedArguments;

        public HITLDecision() {
        }

        public HITLDecision(String decision, String comment, Map<String, Object> editedArguments) {
            this.decision = decision;
            this.comment = comment;
            this.editedArguments = editedArguments != null ? editedArguments : Collections.emptyMap();
        }

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public Map<String, Object> getEditedArguments() { return editedArguments; }
        public void setEditedArguments(Map<String, Object> editedArguments) { this.editedArguments = editedArguments; }
    }

    /**
     * HITL 事件回调接口
     */
    public interface HITLEventCallback {
        void onEvent(String eventType, String message, Map<String, Object> data);
    }
}
