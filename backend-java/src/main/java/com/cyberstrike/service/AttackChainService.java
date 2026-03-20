package com.cyberstrike.service;

import com.cyberstrike.entity.AttackChainEdge;
import com.cyberstrike.entity.AttackChainNode;
import com.cyberstrike.entity.Config;
import com.cyberstrike.entity.Message;
import com.cyberstrike.repository.*;
import com.cyberstrike.service.openai.OpenAiService;
import com.cyberstrike.service.openai.model.OpenAIModels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻击链构建服务
 * 基于对话历史使用 AI 生成攻击链可视化
 */
@Service
public class AttackChainService {

    private static final Logger log = LoggerFactory.getLogger(AttackChainService.class);

    private final AttackChainNodeRepository nodeRepository;
    private final AttackChainEdgeRepository edgeRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final ConfigRepository configRepository;

    // 用于防止同一对话的并发生成
    private final ConcurrentHashMap<String, Boolean> generatingLocks = new ConcurrentHashMap<>();

    public AttackChainService(
            AttackChainNodeRepository nodeRepository,
            AttackChainEdgeRepository edgeRepository,
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            OpenAiService openAiService, ConfigRepository configRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.openAiService = openAiService;
        this.configRepository = configRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取攻击链（如果不存在则生成）
     */
    public Map<String, Object> getOrGenerateChain(String conversationId) {
        // 检查对话是否存在
        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException("对话不存在: " + conversationId);
        }

        // 尝试从数据库加载
        List<AttackChainNode> nodes = nodeRepository.findByConversationId(conversationId);
        List<AttackChainEdge> edges = edgeRepository.findByConversationId(conversationId);

        if (!nodes.isEmpty()) {
            log.info("返回已存在的攻击链: {}", conversationId);
            return Map.of("nodes", nodes, "edges", edges);
        }

        // 如果正在生成中，返回错误
        if (generatingLocks.putIfAbsent(conversationId, true) != null) {
            throw new IllegalStateException("攻击链正在生成中，请稍后再试");
        }

        try {
            return generateChain(conversationId);
        } finally {
            generatingLocks.remove(conversationId);
        }
    }

    /**
     * 重新生成攻击链
     */
    public Map<String, Object> regenerateChain(String conversationId) {
        // 检查对话是否存在
        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException("对话不存在: " + conversationId);
        }

        // 如果正在生成中，返回错误
        if (generatingLocks.putIfAbsent(conversationId, true) != null) {
            throw new IllegalStateException("攻击链正在生成中，请稍后再试");
        }

        try {
            // 删除旧的攻击链
            deleteChain(conversationId);
            return generateChain(conversationId);
        } finally {
            generatingLocks.remove(conversationId);
        }
    }

    /**
     * 生成攻击链
     */
    private Map<String, Object> generateChain(String conversationId) {
        log.info("开始生成攻击链: {}", conversationId);

        // 获取对话消息
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            log.info("对话中没有消息: {}", conversationId);
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        // 检查是否有工具执行
        boolean hasToolExecutions = messages.stream()
                .filter(m -> "assistant".equalsIgnoreCase(m.getRole()))
                .anyMatch(m -> m.getMcpExecutionIds() != null && !m.getMcpExecutionIds().isEmpty());

        if (!hasToolExecutions) {
            log.info("没有工具执行记录，返回空攻击链: {}", conversationId);
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        // 构建对话历史文本
        StringBuilder reactInput = new StringBuilder();
        for (Message msg : messages) {
            reactInput.append(String.format("[%s]: %s\n\n", msg.getRole(), msg.getContent()));
            if (msg.getMcpExecutionIds() != null && !msg.getMcpExecutionIds().isEmpty()) {
                reactInput.append("工具执行ID:\n").append(msg.getMcpExecutionIds()).append("\n\n");
            }
        }

        // 获取最后一条 assistant 消息作为模型输出
        String modelOutput = messages.stream()
                .filter(m -> "assistant".equalsIgnoreCase(m.getRole()))
                .reduce((first, second) -> second)
                .map(Message::getContent)
                .orElse("");

        // 构建 prompt
        String prompt = buildPrompt(reactInput.toString(), modelOutput);

        try {
            // 调用 AI 生成攻击链
            String chainJson = callAiForChainGeneration(prompt);

            // 解析 JSON
            Map<String, Object> chainData = parseChainJson(chainJson, conversationId);

            // 保存到数据库
            saveChain(conversationId, chainData);

            log.info("攻击链生成完成: {} ({} 个节点, {} 条边)",
                    conversationId,
                    ((List<?>) chainData.get("nodes")).size(),
                    ((List<?>) chainData.get("edges")).size());

            return chainData;
        } catch (Exception e) {
            log.error("生成攻击链失败: {}", e.getMessage(), e);
            return Map.of("nodes", List.of(), "edges", List.of(), "error", e.getMessage());
        }
    }

    /**
     * 调用 AI 生成攻击链
     */
    private String callAiForChainGeneration(String prompt) {
        OpenAIModels.ChatCompletionMessage systemMsg = new OpenAIModels.ChatCompletionMessage();
        systemMsg.setRole("system");
        systemMsg.setContent("你是一个专业的安全测试分析师，擅长构建攻击链图。请严格按照JSON格式返回攻击链数据。");

        OpenAIModels.ChatCompletionMessage userMsg = new OpenAIModels.ChatCompletionMessage();
        userMsg.setRole("user");
        userMsg.setContent(prompt);

        List<OpenAIModels.ChatCompletionMessage> messages = List.of(systemMsg, userMsg);

        OpenAIModels.ChatCompletionRequest request = new OpenAIModels.ChatCompletionRequest();
        request.setMessages(messages);
        request.setModel(getCurrentModel());
        request.setTemperature(0.3);
        request.setMaxTokens(8000);

        OpenAIModels.ChatCompletionResponse response = openAiService.chatCompletion(request);

        if (response.getChoices().isEmpty()) {
            throw new RuntimeException("API 未返回有效响应");
        }

        String content = response.getChoices().get(0).getMessage().getContent().trim();

        // 清理 markdown 代码块
        content = content.replaceFirst("^```json\\s*", "");
        content = content.replaceFirst("^```\\s*", "");
        content = content.replaceFirst("\\s*```$", "");

        return content.trim();
    }

    /**
     * 获取当前配置的模型名称
     * 每次调用都会查询数据库，确保获取的是最新值
     */
    private String getCurrentModel() {
        try {
            Long configId = 1L;
            return configRepository.findById(configId)
                    .map(Config::getModel)
                    .filter(model -> !model.trim().isEmpty())
                    .orElse("gpt-3.5-turbo");
        } catch (Exception e) {
            log.warn("获取模型配置时发生异常，使用默认模型 gpt-3.5-turbo", e);
            return "gpt-3.5-turbo";
        }
    }

    /**
     * 解析攻击链 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseChainJson(String json, String conversationId) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        List<AttackChainNode> nodes = new ArrayList<>();
        Map<String, String> nodeIdMap = new HashMap<>(); // AI 返回的 ID -> 新的 UUID

        JsonNode nodesArray = root.get("nodes");
        if (nodesArray != null && nodesArray.isArray()) {
            for (JsonNode n : nodesArray) {
                String originalId = n.get("id").asText();
                String newId = "node_" + UUID.randomUUID().toString();
                nodeIdMap.put(originalId, newId);

                AttackChainNode node = new AttackChainNode();
                node.setId(newId);
                node.setConversationId(conversationId);
                node.setType(n.get("type").asText());
                node.setLabel(n.get("label").asText());
                node.setRiskScore(n.has("risk_score") ? n.get("risk_score").asInt() : 0);
                node.setMetadata(n.has("metadata") ? objectMapper.writeValueAsString(n.get("metadata")) : "{}");
                node.setCreatedAt(LocalDateTime.now());
                nodes.add(node);
            }
        }

        List<AttackChainEdge> edges = new ArrayList<>();
        JsonNode edgesArray = root.get("edges");
        if (edgesArray != null && edgesArray.isArray()) {
            for (JsonNode e : edgesArray) {
                String source = nodeIdMap.get(e.get("source").asText());
                String target = nodeIdMap.get(e.get("target").asText());

                if (source == null || target == null) {
                    continue; // 跳过无效边
                }

                AttackChainEdge edge = new AttackChainEdge();
                edge.setId("edge_" + UUID.randomUUID().toString());
                edge.setConversationId(conversationId);
                edge.setSource(source);
                edge.setTarget(target);
                edge.setType(e.has("type") ? e.get("type").asText() : "leads_to");
                edge.setWeight(e.has("weight") ? e.get("weight").asInt() : 1);
                edge.setCreatedAt(LocalDateTime.now());
                edges.add(edge);
            }
        }

        return Map.of("nodes", nodes, "edges", edges);
    }

    /**
     * 保存攻击链到数据库
     */
    @SuppressWarnings("unchecked")
    private void saveChain(String conversationId, Map<String, Object> chainData) {
        List<AttackChainNode> nodes = (List<AttackChainNode>) chainData.get("nodes");
        List<AttackChainEdge> edges = (List<AttackChainEdge>) chainData.get("edges");

        nodeRepository.saveAll(nodes);
        edgeRepository.saveAll(edges);
    }

    /**
     * 删除攻击链
     */
    public void deleteChain(String conversationId) {
        List<AttackChainNode> nodes = nodeRepository.findByConversationId(conversationId);
        List<AttackChainEdge> edges = edgeRepository.findByConversationId(conversationId);
        nodeRepository.deleteAll(nodes);
        edgeRepository.deleteAll(edges);
    }

    /**
     * 构建 AI prompt
     */
    private String buildPrompt(String reactInput, String modelOutput) {
        return String.format(
                """
                        你是专业的安全测试分析师。根据对话记录构建攻击链图。

                        ## 节点类型
                        - **target**: 测试目标（IP、域名）
                        - **action**: 工具执行（成功或失败）
                        - **vulnerability**: 确认的漏洞

                        ## 边的类型
                        - **leads_to**: 导致/引导到
                        - **discovers**: 发现（action→vulnerability）
                        - **enables**: 使能

                        ## 对话历史

                        %s

                        ## 模型输出

                        %s

                        ## 输出格式

                        严格按照以下JSON格式输出：

                        {
                          "nodes": [
                            {"id": "node_1", "type": "target", "label": "目标描述", "risk_score": 0, "metadata": {"target": "example.com"}},
                            {"id": "node_2", "type": "action", "label": "执行操作描述", "risk_score": 0, "metadata": {"tool_name": "nmap", "findings": ["发现1", "发现2"]}}
                          ],
                          "edges": [
                            {"source": "node_1", "target": "node_2", "type": "leads_to", "weight": 3}
                          ]
                        }

                        **规则：**
                        1. 只使用实际执行的工具和结果
                        2. 节点 ID 按顺序编号，边的 source < target
                        3. 如果没有工具执行，返回空数组

                        开始分析并构建攻击链：
                        """,
                reactInput.length() > 10000 ? reactInput.substring(0, 10000) + "...(截断)" : reactInput,
                modelOutput.length() > 3000 ? modelOutput.substring(0, 3000) + "...(截断)" : modelOutput);
    }
}
