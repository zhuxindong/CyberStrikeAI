package com.cyberstrike.service;

import com.cyberstrike.dto.AgentListResponse;
import com.cyberstrike.dto.AgentListItem;
import com.cyberstrike.entity.AgentEntity;
import com.cyberstrike.entity.AgentMetadata;
import com.cyberstrike.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFileService {

    private final AgentRepository agentRepository;

    public static final String ORCHESTRATOR_KIND = "orchestrator";
    public static final String PLAN_EXECUTE_KIND = "plan_execute";
    public static final String SUPERVISOR_KIND = "supervisor";

    // ==================== 列表接口 ====================

    public AgentListResponse listAgentsForUI() {
        List<AgentEntity> allAgents = agentRepository.findAll();

        List<AgentListItem> items = allAgents.stream()
                .map(this::convertToListItem)
                .sorted(Comparator.comparing(AgentListItem::getName))
                .collect(Collectors.toList());

        return AgentListResponse.builder()
                .agents(items)
                .dir("database")
                .build();
    }

    public AgentMetadata getAgent(String filename) {
        AgentEntity entity = agentRepository.findByFilename(filename)
                .orElseThrow(() -> new RuntimeException("Agent 不存在: " + filename));
        return convertToMetadata(entity);
    }

    @Transactional
    public AgentMetadata createAgent(String filename, AgentMetadata request) {
        if (agentRepository.existsByFilename(filename)) {
            throw new RuntimeException("Agent 已存在: " + filename);
        }

        // ========== 方案2：如果 orchestrator 为 true 但 kind 为空，自动设置 kind ==========
        if (request.isOrchestrator() && (request.getKind() == null || request.getKind().isEmpty())) {
            request.setKind(ORCHESTRATOR_KIND);
            log.info("自动设置 kind = orchestrator，因为 orchestrator 标志为 true");
        }
        // ============================================================================

        // 检查主代理冲突（同类型只能有一个）
        checkOrchestratorConflictForCreate(request.getKind());

        AgentEntity entity = AgentEntity.builder()
                .filename(filename)
                .agentId(request.getAgentId())
                .name(request.getName())
                .description(request.getDescription())
                .instruction(request.getInstruction())
                .tools(request.getTools())
                .maxIterations(request.getMaxIterations())
                .bindRole(request.getBindRole())
                .kind(request.getKind())
                .isOrchestrator(request.isOrchestrator())
                .build();

        AgentEntity saved = agentRepository.save(entity);
        log.info("创建 Agent: {}", filename);
        return convertToMetadata(saved);
    }

    @Transactional
    public AgentMetadata updateAgent(String filename, AgentMetadata request) {
        AgentEntity entity = agentRepository.findByFilename(filename)
                .orElseThrow(() -> new RuntimeException("Agent 不存在: " + filename));

        // ========== 方案2：如果 orchestrator 为 true 但 kind 为空，自动设置 kind ==========
        if (request.isOrchestrator() && (request.getKind() == null || request.getKind().isEmpty())) {
            request.setKind(ORCHESTRATOR_KIND);
            log.info("自动设置 kind = orchestrator，因为 orchestrator 标志为 true");
        }
        // ============================================================================

        String finalFilename = filename;

        // 处理文件名变更
        String newFilename = request.getFilename();
        boolean filenameChanged = newFilename != null && !filename.equals(newFilename);

        if (filenameChanged) {
            if (agentRepository.existsByFilename(newFilename)) {
                throw new RuntimeException("目标文件名已存在: " + newFilename);
            }
            entity.setFilename(newFilename);
            finalFilename = newFilename;
        }

        // 如果 kind 改变，检查主代理冲突
        String newKind = request.getKind();
        boolean kindChanged = newKind != null && !newKind.equals(entity.getKind());

        if (kindChanged && isOrchestratorKind(newKind)) {
            checkOrchestratorConflictForUpdate(entity.getId(), newKind);
        }

        // 更新字段
        if (request.getAgentId() != null) entity.setAgentId(request.getAgentId());
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getInstruction() != null) entity.setInstruction(request.getInstruction());
        if (request.getTools() != null) entity.setTools(request.getTools());
        if (request.getMaxIterations() != null) entity.setMaxIterations(request.getMaxIterations());
        if (request.getBindRole() != null) entity.setBindRole(request.getBindRole());
        if (kindChanged) {
            entity.setKind(newKind);
            entity.setOrchestrator(isOrchestratorKind(newKind));
        }

        AgentEntity saved = agentRepository.save(entity);
        log.info("更新 Agent: {} -> {}", filename, saved.getFilename());
        return convertToMetadata(saved);
    }

    @Transactional
    public boolean deleteAgent(String filename) {
        AgentEntity entity = agentRepository.findByFilename(filename)
                .orElse(null);
        if (entity == null) {
            return false;
        }

        if (entity.isOrchestrator()) {
            throw new RuntimeException("不能删除主代理");
        }

        agentRepository.delete(entity);
        log.info("删除 Agent: {}", filename);
        return true;
    }

    // ==================== 主代理冲突检查 ====================

    /**
     * 创建时检查主代理冲突
     * 同类型的主代理只能有一个
     */
    private void checkOrchestratorConflictForCreate(String kind) {
        if (!isOrchestratorKind(kind)) {
            return; // 不是主代理，无需检查
        }

        String targetType = getNormalizedKind(kind);
        List<AgentEntity> existingOrchestrators = agentRepository.findByIsOrchestrator(true);

        for (AgentEntity existing : existingOrchestrators) {
            String existingType = getNormalizedKind(existing.getKind());
            if (targetType.equals(existingType)) {
                throw new RuntimeException("已存在 " + targetType + " 类型的主代理: " + existing.getFilename());
            }
        }
    }

    /**
     * 更新时检查主代理冲突（排除自身）
     */
    private void checkOrchestratorConflictForUpdate(Integer excludeId, String kind) {
        if (!isOrchestratorKind(kind)) {
            return;
        }

        String targetType = getNormalizedKind(kind);
        List<AgentEntity> existingOrchestrators = agentRepository.findByIsOrchestrator(true);

        for (AgentEntity existing : existingOrchestrators) {
            if (existing.getId().equals(excludeId)) {
                continue; // 跳过自身
            }
            String existingType = getNormalizedKind(existing.getKind());
            if (targetType.equals(existingType)) {
                throw new RuntimeException("已存在 " + targetType + " 类型的主代理: " + existing.getFilename());
            }
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isOrchestratorKind(String kind) {
        if (kind == null) return false;
        String lower = kind.toLowerCase();
        return ORCHESTRATOR_KIND.equals(lower) ||
                PLAN_EXECUTE_KIND.equals(lower) ||
                SUPERVISOR_KIND.equals(lower);
    }

    private String getNormalizedKind(String kind) {
        if (kind == null) return ORCHESTRATOR_KIND;
        String lower = kind.toLowerCase();
        if (lower.equals(PLAN_EXECUTE_KIND) || lower.equals("plan-execute")) {
            return PLAN_EXECUTE_KIND;
        }
        if (lower.equals(SUPERVISOR_KIND)) {
            return SUPERVISOR_KIND;
        }
        return ORCHESTRATOR_KIND;
    }

    // ==================== 转换方法 ====================

    private AgentListItem convertToListItem(AgentEntity entity) {
        return AgentListItem.builder()
                .id(entity.getAgentId() != null ? entity.getAgentId() : String.valueOf(entity.getId()))
                .name(entity.getName())
                .description(entity.getDescription())
                .filename(entity.getFilename())
                .kind(entity.getKind() != null ? entity.getKind() : "")
                .isOrchestrator(entity.isOrchestrator())
                .build();
    }

    private AgentMetadata convertToMetadata(AgentEntity entity) {
        return AgentMetadata.builder()
                .id(entity.getId())
                .filename(entity.getFilename())
                .agentId(entity.getAgentId())
                .name(entity.getName())
                .description(entity.getDescription())
                .instruction(entity.getInstruction())
                .tools(entity.getTools())
                .maxIterations(entity.getMaxIterations())
                .bindRole(entity.getBindRole())
                .kind(entity.getKind())
                .orchestrator(entity.isOrchestrator())
                .build();
    }

    /**
     * 获取所有 Agent（用于子代理管理）
     */
    public List<AgentMetadata> getAllAgents() {
        List<AgentEntity> entities = agentRepository.findAll();
        return entities.stream()
                .map(this::convertToMetadata)
                .collect(Collectors.toList());
    }
}