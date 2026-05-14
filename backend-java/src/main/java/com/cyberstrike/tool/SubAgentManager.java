package com.cyberstrike.tool;

import com.cyberstrike.entity.AgentMetadata;
import com.cyberstrike.service.AgentFileService;
import com.cyberstrike.service.openai.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SubAgentManager {

    private final AgentFileService agentFileService;
    private final OpenAiService openAiService;

    private final Map<String, SubAgent> subAgentCache = new ConcurrentHashMap<>();
    private final Map<String, String> subAgentTypeMap = new ConcurrentHashMap<>();

    public SubAgentManager(AgentFileService agentFileService, OpenAiService openAiService) {
        this.agentFileService = agentFileService;
        this.openAiService = openAiService;
    }

    @PostConstruct
    public void init() {
        loadSubAgents();
    }

    /**
     * 从数据库加载所有子代理
     */
    public void loadSubAgents() {
        try {
            List<AgentMetadata> agents = agentFileService.getAllAgents();
            subAgentTypeMap.clear();
            subAgentCache.clear();

            for (AgentMetadata agent : agents) {
                // 只加载非主代理
                if (!agent.isOrchestrator()) {
                    String type = agent.getAgentId() != null ? agent.getAgentId() : String.valueOf(agent.getId());
                    subAgentTypeMap.put(type, agent.getFilename());
                    log.info("注册子代理: {} -> {}", type, agent.getFilename());
                }
            }
            log.info("子代理加载完成，共 {} 个", subAgentTypeMap.size());
        } catch (Exception e) {
            log.error("加载子代理失败", e);
        }
    }

    /**
     * 获取子代理
     */
    public SubAgent getSubAgent(String subagentType) {
        return subAgentCache.computeIfAbsent(subagentType, type -> {
            String filename = subAgentTypeMap.get(type);
            if (filename == null) {
                log.warn("未找到子代理: {}", type);
                return null;
            }
            try {
                AgentMetadata agent = agentFileService.getAgent(filename);
                if (agent == null) {
                    return null;
                }
                return new SubAgent(agent, openAiService);
            } catch (Exception e) {
                log.error("创建子代理失败: {}", type, e);
                return null;
            }
        });
    }

    /**
     * 获取所有可用的子代理类型列表
     */
    public List<String> getAvailableSubAgentTypes() {
        return subAgentTypeMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * 检查子代理是否存在
     */
    public boolean hasSubAgent(String subagentType) {
        return subAgentTypeMap.containsKey(subagentType);
    }

    /**
     * 刷新子代理列表
     */
    public void refresh() {
        loadSubAgents();
    }
}