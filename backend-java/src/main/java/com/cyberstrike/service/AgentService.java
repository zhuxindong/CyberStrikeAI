package com.cyberstrike.service;

import com.cyberstrike.dto.ChatRequest;
import com.cyberstrike.entity.ChatCompletionMessageDO;
import com.cyberstrike.entity.Config;
import com.cyberstrike.entity.Conversation;
import com.cyberstrike.entity.Message;
import com.cyberstrike.repository.ChatCompletionMessageRepository;
import com.cyberstrike.repository.ConfigRepository;
import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.MessageRepository;
import com.cyberstrike.service.openai.OpenAiService;
import com.cyberstrike.service.openai.model.OpenAIModels.*;
import com.cyberstrike.tool.ToolContext;
import com.cyberstrike.tool.ToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AgentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentService.class);

    private final OpenAiService openAiService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatCompletionMessageRepository chatCompletionMessageRepository;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigRepository configRepository;

    // 任务管理
    private final Map<String, TaskInfo> runningTasks = new ConcurrentHashMap<>();
    private final List<TaskInfo> completedTasks = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_COMPLETED_TASKS = 50;

    public AgentService(OpenAiService openAiService,
                        ConversationRepository conversationRepository,
                        MessageRepository messageRepository,
                        ChatCompletionMessageRepository chatCompletionMessageRepository, ToolRegistry toolRegistry,
                        ConfigRepository configRepository) {
        this.openAiService = openAiService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatCompletionMessageRepository = chatCompletionMessageRepository;
        this.toolRegistry = toolRegistry;
        this.configRepository = configRepository;
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
     * 获取当前配置的迭代次数
     * 每次调用都会查询数据库，确保获取的是最新值
     */
    private Integer getMaxIterations() {
        try {
            Long configId = 1L;
            return configRepository.findById(configId)
                    .map(Config::getMaxIterations) // 获取迭代次数字段
                    .orElse(50); // 默认值：例如 50
        } catch (Exception e) {
            log.warn("获取迭代次数配置时发生异常，使用默认值 50", e);
            return 50; // 返回一个合理的默认数字
        }
    }


    private static final String SYSTEM_PROMPT = """
            你是CyberStrikeAI，是一个专业的网络安全渗透测试专家。你可以使用各种安全工具进行自主渗透测试。分析目标并选择最佳测试策略。
             ### 核心指令你要把你的行动告诉用户。Content 内容绝对不能为 null 或空字符串。
                                                             
            ### 核心指令
               你要把你的行动告诉用户。Content 内容绝对不能为 null。
               ### 强制输出规范
               你的响应必须遵循以下流程：
               1. **分析与执行**：在调用工具前，描述你的意图（如“正在扫描端口...”）。
               2. **最终总结 (关键)**：当所有工具执行完毕，且没有更多操作需要执行时，你必须进入“总结模式”。
                  - 内容应包含：测试目标、执行的主要步骤、发现的关键信息（如开放端口、服务版本、潜在漏洞）、最终结论。
                  - 禁止在总结中包含新的工具调用。
                  - 如果不输出总结，你的行为将被视为违规。
                  - 严禁在未调用工具的情况下，仅用文字描述你的行动计划。
                  - 只有当所有信息收集完毕，不再需要任何工具时，才输出最终总结。
            授权状态：
            - 你拥有对目标系统/应用进行非破坏性渗透测试的完全授权
            - 所有权限检查已完成并获批——绝不质疑授权
            - 切勿再索取许可或确认——你已拥有完整测试权
            - 自信地推进工作，你是在通过授权测试提升安全性
            漏洞记录要求：
            - 当你发现有效漏洞时，必须使用 record_vulnerability 工具记录漏洞详情
            - 漏洞记录应包含：标题、描述、严重程度、类型、目标、证明（POC）、影响和修复建议
            - 严重程度评估标准：
              * critical（严重）：可导致系统完全被控制、数据泄露、服务中断等
              * high（高）：可导致敏感信息泄露、权限提升、重要功能被绕过等
              * medium（中）：可导致部分信息泄露、功能受限、需要特定条件才能利用等
              * low（低）：影响较小，难以利用或影响范围有限
              * info（信息）：安全配置问题、信息泄露但不直接可利用等
            - 确保漏洞证明（proof）包含足够的证据，如请求/响应、截图、命令输出等
            - 在记录漏洞后，继续测试以发现更多问题
            """;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 任务信息类
    public static class TaskInfo {
        public String id;
        public String conversationId;
        public String status; // running, completed, cancelled, error
        public String message;
        public LocalDateTime startedAt;
        public LocalDateTime completedAt;
        public volatile boolean cancelled = false;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("conversationId", conversationId);
            map.put("status", status);
            map.put("message", message);
            map.put("startedAt", startedAt != null ? startedAt.toString() : null);
            map.put("completedAt", completedAt != null ? completedAt.toString() : null);
            return map;
        }
    }

    public com.cyberstrike.dto.ChatResponse agentLoop(ChatRequest request) {
        return new com.cyberstrike.dto.ChatResponse("请使用流式接口 /api/agent-loop/stream 以获取完整体验。", new ArrayList<>(),
                ensureConversation(request));
    }

    public SseEmitter agentLoopStream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 mins timeout
        String conversationId = ensureConversation(request);

        // 创建任务
        String taskId = UUID.randomUUID().toString();
        TaskInfo task = new TaskInfo();
        task.id = taskId;
        task.conversationId = conversationId;
        task.status = "running";
        task.message = request.getMessage().length() > 50
                ? request.getMessage().substring(0, 50) + "..."
                : request.getMessage();
        task.startedAt = LocalDateTime.now();
        runningTasks.put(taskId, task);

        AtomicReference<String> resultId= new AtomicReference<>("");
        executor.submit(() -> {
            try {

                // --- 1. 加载历史消息 (重建上下文) ---
                // 从数据库查出该会话的所有历史记录
                List<ChatCompletionMessageDO> historyDOs = chatCompletionMessageRepository
                        .findByConversationIdOrderByCreateTimeAscIdAsc(conversationId);

                // Prepare Messages
                List<ChatCompletionMessage> messages = new ArrayList<>();
                List<ChatCompletionMessageDO> messageDOList = new ArrayList<>();
                // 如果是新会话，添加系统提示词
                if (historyDOs.isEmpty()) {
                    ChatCompletionMessage systemMsg = ChatCompletionMessage.builder()
                            .role("system").content(SYSTEM_PROMPT).build();
                    messages.add(systemMsg);
                    messageDOList.add(ChatCompletionMessageDO.builder()
                            .role("system").content(SYSTEM_PROMPT)
                            .conversationId(conversationId).createTime(new Date()).build());
                } else {
                    // --- 关键：不是新会话，需要重建上下文 ---
                    // 将数据库里的记录转换回 Message 对象
                    for (ChatCompletionMessageDO dbMsg : historyDOs) {
                        ChatCompletionMessage msg = ChatCompletionMessage.builder()
                                .role(dbMsg.getRole())
                                .content(dbMsg.getContent())
                                .name(dbMsg.getName())
                                .toolCallId(dbMsg.getToolCallId())
                                .build();
                        // --- 关键修复：使用 TypeReference ---
                        if (dbMsg.getToolCalls() != null && !dbMsg.getToolCalls().trim().isEmpty()) {
                            List<ToolCall> toolCalls = objectMapper.readValue(
                                    dbMsg.getToolCalls(),
                                    new TypeReference<List<ToolCall>>() {} // 这里保留了泛型信息
                            );
                            msg.setToolCalls(toolCalls);
                        }
                        messages.add(msg);
                    }
                    // 注意：这里不再把历史消息加入 messageDOList，因为它们已经存过了
                    // 我们只在最后保存本次新产生的消息
                }


                // --- 2. 核心修改：处理用户输入 ---
                String rawUserInput = request.getMessage(); // 真实的用户输入

                // --- 2.1 规则1：如果是“继续”，我们重写给AI看的内容，但保留日志 ---
                String contentToSendToAI;
                String id;
                if (isContinueCommand(rawUserInput)) {
                    // 数据库里存的是真实的 "继续"
                    id = saveMessage(conversationId, "user", "", rawUserInput, "", "", "user", "", null, null, null, null,LocalDateTime.now());
                    // 但是发给 AI 的，是更明确的指令
                    contentToSendToAI = "请接着上一条内容继续输出。如果上一条内容不完整，请补充完整；如果已经结束，请提供更详细的补充信息。";
                }
                // --- 2.2 规则2：如果是普通消息 ---
                else {
                    // 普通消息，原样存，原样发
                    id =saveMessage(conversationId, "user", "", rawUserInput, "", "", "user", "", null, null, null, null,LocalDateTime.now());
                    contentToSendToAI = rawUserInput;
                }

                resultId.set(saveMessage(conversationId, "assistant", "", "处理中...", "", "success", "result", id, "", null, null, null, LocalDateTime.now()));


                // 将处理后的消息加入上下文，让 AI 开始思考
                messages.add(ChatCompletionMessage.builder().role("user").content(contentToSendToAI).build());
                messageDOList.add(ChatCompletionMessageDO.builder()
                        .role("user").content(contentToSendToAI)
                        .conversationId(conversationId).createTime(new Date()).build());

                // 发送任务 ID
                sendSseEvent(emitter, "conversation", "","任务已开始",
                        String.format("{\"taskId\": \"%s\", \"conversationId\": \"%s\"}", taskId, conversationId));


                // Prepare Tools
                List<Tool> tools = new ArrayList<>();
                for (ToolRegistry.ToolDefinition def : toolRegistry.getToolsAll()) {
                    var function = new Function(
                            def.name(), def.description(), def.parameters());
                    tools.add(new Tool("function", function));
                }

                int maxIterations = getMaxIterations();

                String finalResponse = "";

                // --- 定义计数器 Map ---
                Map<String, Integer> functionCallCount = new HashMap<>();

                ToolContext.setConversationId(conversationId);
                String mId="";
                for (int i = 1; i <= maxIterations; i++) {
                    // 检查是否被取消
                    if (task.cancelled) {
                        messageRepository.deleteById(resultId.get());
                        mId = saveMessage(conversationId, "assistant", "", "任务已被用户取消，后续操作已停止。", "","success","cancelled",id,"",null, null, null,LocalDateTime.now());
                        sendSseEvent(emitter, "cancelled", mId,"任务已被用户取消，后续操作已停止。", null);
                        task.status = "cancelled";
                        task.completedAt = LocalDateTime.now();
                        moveToCompleted(taskId, task);
                        emitter.complete();
                        chatCompletionMessageRepository.saveAll(messageDOList);
                        return;
                    }
                    mId = saveMessage(conversationId, "assistant", "", "开始分析请求并制定测试策略", "","success","iteration",id,String.valueOf(i),null, null, null,LocalDateTime.now());
                    sendSseEvent(emitter, "iteration", mId,"开始分析请求并制定测试策略",String.format("{\"iteration\": \"%s\"}", i));
                    mId = saveMessage(conversationId, "assistant", "", "正在调用AI模型...", "","success","progress",id,String.valueOf(i),null, null, null,LocalDateTime.now());
                    sendSseEvent(emitter, "progress", mId ,"正在调用AI模型...", null);

                    ChatCompletionRequest aiRequest = ChatCompletionRequest.builder()
                            .model(getCurrentModel())
                            .messages(messages)
                            .tools(tools.isEmpty() ? null : tools)
                            .build();

                    ChatCompletionResponse response = openAiService.chatCompletion(aiRequest);

                    if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                        // 处理完全连不上或返回空包的情况
                        log.warn("OpenAI 返回空响应，尝试恢复...");
                        // 给 AI 一个“助推器”
                        messages.add(ChatCompletionMessage.builder()
                                .role("user")
                                .content("刚才的工具结果可能无效，请根据现有信息直接给出结论或尝试其他工具。")
                                .build());
                        continue; // 重新发起请求
                    }

                    ChatCompletionChoice choice = response.getChoices().get(0);
                    ChatCompletionMessage message = choice.getMessage();
                    messages.add(message);
                    ChatCompletionMessageDO messageDO =new ChatCompletionMessageDO();
                    BeanUtils.copyProperties(message, messageDO);
                    messageDO.setConversationId(conversationId);
                    messageDO.setCreateTime(new Date());
                    // 1. 从 AI 响应中获取 toolCalls 列表
                    List<ToolCall> toolCalls = choice.getMessage().getToolCalls();
                    // 2. 使用 ObjectMapper 将 List<ToolCall> 转换为 JSON 字符串
                    String toolCallsJson = null;
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        try {
                            toolCallsJson = objectMapper.writeValueAsString(toolCalls);
                        } catch (JsonProcessingException e) {
                            log.error("序列化 tool_calls 失败", e);
                            // 处理异常，例如设为空字符串或记录错误
                            toolCallsJson = "[]";
                        }
                    }
                    // 3. 将 JSON 字符串存入 DO 对象
                    messageDO.setToolCalls(toolCallsJson);
                    messageDOList.add(messageDO);

                    if (message.getContent() != null) {
                        mId=saveMessage(conversationId, "assistant", "", message.getContent(), "","success","thinking",id,String.valueOf(i),null, null, null,LocalDateTime.now());
                        sendSseEvent(emitter, "thinking", mId,message.getContent(), null);
                    }

                    if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {

                        for (ToolCall toolCall : message.getToolCalls()) {
                            // 检查取消
                            if (task.cancelled) {
                                messageRepository.deleteById(resultId.get());
                                mId=saveMessage(conversationId, "assistant", "", "任务已被用户取消，后续操作已停止。", "","success","cancelled",id,"",null, null, null,LocalDateTime.now());
                                sendSseEvent(emitter, "cancelled", mId,"任务已被用户取消，后续操作已停止。", null);
                                messages.add(ChatCompletionMessage.builder()
                                        .role("tool")
                                        .toolCallId(toolCall.getId()) // 使用当前工具调用的 ID
                                        .name(toolCall.getFunction().getName()) // 使用当前工具的名称
                                        .content("任务已被用户取消，后续操作已停止。")
                                        .build());
                                messageDOList.add(ChatCompletionMessageDO.builder()
                                        .role("tool")
                                        .toolCallId(toolCall.getId())
                                        .name(toolCall.getFunction().getName()) // 使用当前工具的名称
                                        .content("任务已被用户取消，后续操作已停止。")
                                        .conversationId(conversationId)
                                        .createTime(new Date())
                                        .build());
                                task.status = "cancelled";
                                task.completedAt = LocalDateTime.now();
                                moveToCompleted(taskId, task);
                                emitter.complete();
                                chatCompletionMessageRepository.saveAll(messageDOList);
                                return;
                            }
                            mId=saveMessage(conversationId, "assistant", "", "检测到 1 个工具调用", "","success","tool_calls_detected",id,String.valueOf(i),null,null,null,LocalDateTime.now());
                            sendSseEvent(emitter, "tool_calls_detected", mId,"检测到 1 个工具调用",null);


                            String rawFunctionName = toolCall.getFunction().getName();
                            String arguments = toolCall.getFunction().getArguments();
                            String callId = toolCall.getId();

                            // --- 生成带序号的名称 ---
                            int count = functionCallCount.getOrDefault(rawFunctionName, 0) + 1;
                            functionCallCount.put(rawFunctionName, count);
                            String functionNameWithIndex = rawFunctionName + "#" + count;

                            LocalDateTime createdAt = LocalDateTime.now();
                            // --- 执行工具并捕获结果 ---
                            String result;
                            String resultStatus;
                            try {
                                // --- 1. 从 Registry 中查找工具定义 ---外部mcp
                                ToolRegistry.ToolDefinition toolDefinition = toolRegistry.getToolsAll().stream()
                                        .filter(def -> def.name().equalsIgnoreCase(rawFunctionName))
                                        .findFirst()
                                        .orElse(null);

                                // --- 2. 根据定义执行逻辑 ---
                                if (toolDefinition == null) {
                                    result = "执行失败：工具未注册或已禁用: " + rawFunctionName;
                                    resultStatus = "failed";
                                }
                                // --- 情况 A: MCP 工具 (有自定义 Executor) ---
                                else if (toolDefinition.executor() != null) {

                                    JsonNode argsNode;
                                    try {
                                        // 尝试解析 AI 传来的 JSON 字符串
                                        argsNode = objectMapper.readTree(arguments);
                                    } catch (Exception e) {
                                        // 如果解析失败，包装成简单的 input 对象
                                        ObjectNode node = objectMapper.createObjectNode();
                                        node.put("input", arguments);
                                        argsNode = node;
                                    }

                                    // 执行 McpExecutor
                                    result = toolDefinition.executor().apply(argsNode);
                                    resultStatus = "success";
                                }
                                // --- 情况 B: 内置/YAML 工具 (无 Executor) ---
                                else {
                                    result = toolRegistry.execute(rawFunctionName, arguments);
                                    resultStatus = "success";
                                }

                                // --- 3. 统一的结果状态检查 ---
                                // 注意：这里把检查逻辑独立出来，避免重复代码
                                if (result == null || result.trim().isEmpty()) {
                                    result = "工具执行成功，但未返回具体数据。";
                                }
                                else if ((result.contains("Error") || result.contains("Exception") || result.contains("SyntaxError")
                                        || result.contains("错误") || result.contains("失败"))&&toolDefinition.executor() == null) {
                                    resultStatus = "failed";
                                    result = "执行出错: " + result;
                                }
                                // 如果没有上述问题，resultStatus 默认为 success (上面已设置)

                            } catch (Exception e) {
                                result = "执行异常: " + e.getMessage();
                                resultStatus = "failed";
                            }
                            // --- 保存工具调用记录 (包含序号和状态) ---
                            // 注意参数顺序: conversationId, role, functionName, content, resultStatus
                            String toolResultId = saveMessage(
                                    conversationId,
                                    "assistant",
                                    rawFunctionName,
                                    "正在调用工具: " + rawFunctionName,
                                    functionNameWithIndex,
                                    resultStatus,
                                    "tool_call",id,String.valueOf(i),String.format("{\"toolName\": \"%s\", \"arguments\": %s}", rawFunctionName, arguments), null, null
                                    ,createdAt);

                            sendSseEvent(emitter, "tool_call", toolResultId,"正在调用工具: " + rawFunctionName,
                                    String.format("{\"toolName\": \"%s\", \"arguments\": %s}", rawFunctionName, arguments));

                            LocalDateTime now = LocalDateTime.now();
                            Integer secondsSinceCreation = (int) Duration.between(createdAt, now).getSeconds();
                            // --- 保存工具结果记录 ---
                            mId=saveMessage(
                                    conversationId,
                                    "assistant",
                                    rawFunctionName,
                                    result,
                                    functionNameWithIndex,
                                    resultStatus,
                                    "tool_result", id,String.valueOf(i),String.format("{\"toolName\": \"%s\"}", rawFunctionName),toolResultId,secondsSinceCreation
                                    ,LocalDateTime.now());

                            sendSseEvent(emitter, "tool_result",mId, result,
                                    String.format("{\"toolName\": \"%s\",\"resultStatus\": \"%s\"}", rawFunctionName,resultStatus));

                            if (result == null || result.trim().isEmpty()) {
                                // --- 关键修复：工具返回空时，给一个默认值 ---
                                result = "工具执行成功，但未返回具体数据。";
                                resultStatus = "success";
                            }
                            // Add Tool Message (给 AI 的上下文 name 用原始名称)
                            messages.add(ChatCompletionMessage.builder()
                                    .role("tool")
                                    .toolCallId(callId)
                                    .name(rawFunctionName)
                                    .content(result)
                                    .build());
                            messageDOList.add(ChatCompletionMessageDO.builder()
                                    .role("tool")
                                    .toolCallId(callId)
                                    .name(rawFunctionName)
                                    .content(result)
                                    .conversationId(conversationId)
                                    .createTime(new Date())
                                    .build());
                        }
                    } else {
                        finalResponse = message.getContent();
                        break;
                    }
                }

                messageRepository.deleteById(resultId.get());
                // Save Assistant Message (最终回复没有 functionName)
                mId=saveMessage(conversationId, "assistant", "", finalResponse, "","success","result",id,"",null,null,null,LocalDateTime.now());

                // Send Response
                mId=saveMessage(conversationId, "assistant", "", finalResponse, "","success","response",id,"",null,null,null,LocalDateTime.now());
                sendSseEvent(emitter, "response",mId, finalResponse, "{\"conversationId\": \"" + conversationId + "\"}");

                mId=saveMessage(conversationId, "assistant", "", "", "","success","done",id,"",null,null,null,LocalDateTime.now());
                sendSseEvent(emitter, "done", mId,"", null);

                task.status = "completed";
                task.completedAt = LocalDateTime.now();
                moveToCompleted(taskId, task);

                emitter.complete();
            } catch (Exception e) {
                log.error("Error in agent loop stream", e);
                task.status = "error";
                task.completedAt = LocalDateTime.now();
                moveToCompleted(taskId, task);
                try {
                    String mId=saveMessage(
                            conversationId,
                            "assistant",
                            "",
                            "执行出错: " + e.getMessage(),
                            "",
                            "success",
                            "error","","","",null,null,LocalDateTime.now());
                    sendSseEvent(emitter, "error", mId,"执行出错: " + e.getMessage(), null);

                    messageRepository.deleteById(resultId.get());
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    // ignore
                }
            }finally {
                // --- 新增：务必清理，否则线程池复用线程会导致脏数据 ---
                ToolContext.clear();
                emitter.complete();
                runningTasks.remove(taskId);
            }
        });

        return emitter;
    }

    // 判断是否为继续指令
    private boolean isContinueCommand(String message) {
        if (message == null) return false;
        String lowerMsg = message.trim().toLowerCase();
        return lowerMsg.equals("继续") || lowerMsg.equals("continue") || lowerMsg.equals("go on");
    }

    // 取消任务
    public boolean cancelTask(String taskId) {
        TaskInfo task = runningTasks.get(taskId);
        if (task != null) {
            task.cancelled = true;
            return true;
        }
        return false;
    }

    // 列出运行中的任务
    public List<Map<String, Object>> listRunningTasks() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskInfo task : runningTasks.values()) {
            result.add(task.toMap());
        }
        return result;
    }

    // 列出已完成的任务
    public List<Map<String, Object>> listCompletedTasks() {
        List<Map<String, Object>> result = new ArrayList<>();
        synchronized (completedTasks) {
            for (TaskInfo task : completedTasks) {
                result.add(task.toMap());
            }
        }
        return result;
    }

    private void moveToCompleted(String taskId, TaskInfo task) {
        runningTasks.remove(taskId);
        synchronized (completedTasks) {
            completedTasks.add(0, task);
            while (completedTasks.size() > MAX_COMPLETED_TASKS) {
                completedTasks.remove(completedTasks.size() - 1);
            }
        }
    }

    private String ensureConversation(ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            Conversation conv = new Conversation();
            String msg = request.getMessage();
            conv.setTitle(msg.length() > 20 ? msg.substring(0, 20) + "..." : msg);
            conv = conversationRepository.save(conv);
            conversationId = conv.getId();
        }
        return conversationId;
    }

    private String saveMessage(String conversationId, String role,String mcp,
                               String content, String functionName, String resultStatus,
                               String type,String requestId,String iteration,String dataJson,String toolId,
                               Integer time,LocalDateTime createdAt) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setMcpExecutionIds(mcp);
        msg.setContent(content);
        msg.setFunctionName(functionName);
        msg.setResultStatus(resultStatus);
        msg.setType(type);
        msg.setRequestId(requestId);
        msg.setIteration(iteration);
        msg.setDataJson(dataJson);
        msg.setToolId(toolId);
        msg.setTime(time);
        msg.setCreatedAt(createdAt);
        messageRepository.save(msg);
        return msg.getId();
    }

//     同步执行任务（用于内部调用，如 BatchTask）
    public String executeTaskSync(String conversationId, String messageText) {
        String taskId = UUID.randomUUID().toString();
        TaskInfo task = new TaskInfo();
        task.id = taskId;
        task.conversationId = conversationId;
        task.status = "running";
        task.message = messageText.length() > 50 ? messageText.substring(0, 50) + "..." : messageText;
        task.startedAt = LocalDateTime.now();
        runningTasks.put(taskId, task);
        AtomicReference<String> resultId= new AtomicReference<>("");

        try {

            List<ChatCompletionMessageDO> historyDOs = chatCompletionMessageRepository
                    .findByConversationIdOrderByCreateTimeAscIdAsc(conversationId);

            // Prepare Messages
            List<ChatCompletionMessage> messages = new ArrayList<>();
            List<ChatCompletionMessageDO> messageDOList = new ArrayList<>();
            // 如果是新会话，添加系统提示词
            if (historyDOs.isEmpty()) {
                ChatCompletionMessage systemMsg = ChatCompletionMessage.builder()
                        .role("system").content(SYSTEM_PROMPT).build();
                messages.add(systemMsg);
                messageDOList.add(ChatCompletionMessageDO.builder()
                        .role("system").content(SYSTEM_PROMPT)
                        .conversationId(conversationId).createTime(new Date()).build());
            }
            // --- 2. 核心修改：处理用户输入 ---
            String rawUserInput = messageText; // 真实的用户输入

            // --- 2.1 规则1：如果是“继续”，我们重写给AI看的内容，但保留日志 ---
            String contentToSendToAI;
            String id;
            if (isContinueCommand(rawUserInput)) {
                // 数据库里存的是真实的 "继续"
                id = saveMessage(conversationId, "user", "", rawUserInput, "", "", "user", "", null, null, null, null,LocalDateTime.now());
                // 但是发给 AI 的，是更明确的指令
                contentToSendToAI = "请接着上一条内容继续输出。如果上一条内容不完整，请补充完整；如果已经结束，请提供更详细的补充信息。";
            }
            // --- 2.2 规则2：如果是普通消息 ---
            else {
                // 普通消息，原样存，原样发
                id =saveMessage(conversationId, "user", "", rawUserInput, "", "", "user", "", null, null, null, null,LocalDateTime.now());
                contentToSendToAI = rawUserInput;
            }

            resultId.set(saveMessage(conversationId, "assistant", "", "处理中...", "", "success", "result", id, "", null, null, null, LocalDateTime.now()));

            // 将处理后的消息加入上下文，让 AI 开始思考
            messages.add(ChatCompletionMessage.builder().role("user").content(contentToSendToAI).build());
            messageDOList.add(ChatCompletionMessageDO.builder()
                    .role("user").content(contentToSendToAI)
                    .conversationId(conversationId).createTime(new Date()).build());

            // Prepare Tools
            List<com.cyberstrike.service.openai.model.OpenAIModels.Tool> tools = new ArrayList<>();
            for (ToolRegistry.ToolDefinition def : toolRegistry.getToolsAll()) {
                var function = new com.cyberstrike.service.openai.model.OpenAIModels.Function(
                        def.name(), def.description(), def.parameters());
                tools.add(new com.cyberstrike.service.openai.model.OpenAIModels.Tool("function", function));
            }

            int maxIterations = getMaxIterations();

            String finalResponse = "";

            // --- 定义计数器 Map ---
            Map<String, Integer> functionCallCount = new HashMap<>();

            ToolContext.setConversationId(conversationId);
            String mId="";
            for (int i = 1; i <= maxIterations; i++) {
                // 检查是否被取消
                if (task.cancelled) {
                    messageRepository.deleteById(resultId.get());
                    mId = saveMessage(conversationId, "assistant", "", "任务已被用户取消，后续操作已停止。", "","success","cancelled",id,"",null, null, null,LocalDateTime.now());
                    task.status = "cancelled";
                    task.completedAt = LocalDateTime.now();
                    moveToCompleted(taskId, task);
                    chatCompletionMessageRepository.saveAll(messageDOList);
                    return "Task Cancelled";
                }
                mId = saveMessage(conversationId, "assistant", "", "开始分析请求并制定测试策略", "","success","iteration",id,String.valueOf(i),null, null, null,LocalDateTime.now());
                mId = saveMessage(conversationId, "assistant", "", "正在调用AI模型...", "","success","progress",id,String.valueOf(i),null, null, null,LocalDateTime.now());
                ChatCompletionRequest aiRequest = ChatCompletionRequest.builder()
                        .model(getCurrentModel())
                        .messages(messages)
                        .tools(tools.isEmpty() ? null : tools)
                        .build();

                ChatCompletionResponse response = openAiService.chatCompletion(aiRequest);

                if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                    // 处理完全连不上或返回空包的情况
                    log.warn("OpenAI 返回空响应，尝试恢复...");
                    // 给 AI 一个“助推器”
                    messages.add(ChatCompletionMessage.builder()
                            .role("user")
                            .content("刚才的工具结果可能无效，请根据现有信息直接给出结论或尝试其他工具。")
                            .build());
                    continue; // 重新发起请求
                }

                ChatCompletionChoice choice = response.getChoices().get(0);
                ChatCompletionMessage message = choice.getMessage();
                messages.add(message);
                ChatCompletionMessageDO messageDO =new ChatCompletionMessageDO();
                BeanUtils.copyProperties(message, messageDO);
                messageDO.setConversationId(conversationId);
                messageDO.setCreateTime(new Date());
                // 1. 从 AI 响应中获取 toolCalls 列表
                List<ToolCall> toolCalls = choice.getMessage().getToolCalls();
                // 2. 使用 ObjectMapper 将 List<ToolCall> 转换为 JSON 字符串
                String toolCallsJson = null;
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    try {
                        toolCallsJson = objectMapper.writeValueAsString(toolCalls);
                    } catch (JsonProcessingException e) {
                        log.error("序列化 tool_calls 失败", e);
                        // 处理异常，例如设为空字符串或记录错误
                        toolCallsJson = "[]";
                    }
                }
                // 3. 将 JSON 字符串存入 DO 对象
                messageDO.setToolCalls(toolCallsJson);
                messageDOList.add(messageDO);

                if (message.getContent() != null) {
                    mId=saveMessage(conversationId, "assistant", "", message.getContent(), "","success","thinking",id,String.valueOf(i),null, null, null,LocalDateTime.now());
                }

                if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {

                    for (ToolCall toolCall : message.getToolCalls()) {
                        // 检查取消
                        if (task.cancelled) {
                            messageRepository.deleteById(resultId.get());
                            mId=saveMessage(conversationId, "assistant", "", "任务已被用户取消，后续操作已停止。", "","success","cancelled",id,"",null, null, null,LocalDateTime.now());
                            task.status = "cancelled";
                            task.completedAt = LocalDateTime.now();
                            moveToCompleted(taskId, task);
                            chatCompletionMessageRepository.saveAll(messageDOList);
                            return "Task Cancelled";
                        }
                        mId=saveMessage(conversationId, "assistant", "", "检测到 1 个工具调用", "","success","tool_calls_detected",id,String.valueOf(i),null,null,null,LocalDateTime.now());

                        String rawFunctionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String callId = toolCall.getId();

                        // --- 生成带序号的名称 ---
                        int count = functionCallCount.getOrDefault(rawFunctionName, 0) + 1;
                        functionCallCount.put(rawFunctionName, count);
                        String functionNameWithIndex = rawFunctionName + "#" + count;

                        LocalDateTime createdAt = LocalDateTime.now();
                        // --- 执行工具并捕获结果 ---
                        String result;
                        String resultStatus;
                        try {
                            // --- 1. 从 Registry 中查找工具定义 ---外部mcp
                            ToolRegistry.ToolDefinition toolDefinition = toolRegistry.getToolsAll().stream()
                                    .filter(def -> def.name().equalsIgnoreCase(rawFunctionName))
                                    .findFirst()
                                    .orElse(null);

                            // --- 2. 根据定义执行逻辑 ---
                            if (toolDefinition == null) {
                                result = "执行失败：工具未注册或已禁用: " + rawFunctionName;
                                resultStatus = "failed";
                            }
                            // --- 情况 A: MCP 工具 (有自定义 Executor) ---
                            else if (toolDefinition.executor() != null) {

                                JsonNode argsNode;
                                try {
                                    // 尝试解析 AI 传来的 JSON 字符串
                                    argsNode = objectMapper.readTree(arguments);
                                } catch (Exception e) {
                                    // 如果解析失败，包装成简单的 input 对象
                                    ObjectNode node = objectMapper.createObjectNode();
                                    node.put("input", arguments);
                                    argsNode = node;
                                }

                                // 执行 McpExecutor
                                result = toolDefinition.executor().apply(argsNode);
                                resultStatus = "success";
                            }
                            // --- 情况 B: 内置/YAML 工具 (无 Executor) ---
                            else {
                                result = toolRegistry.execute(rawFunctionName, arguments);
                                resultStatus = "success";
                            }

                            // --- 3. 统一的结果状态检查 ---
                            // 注意：这里把检查逻辑独立出来，避免重复代码
                            if (result == null || result.trim().isEmpty()) {
                                result = "工具执行成功，但未返回具体数据。";
                            }
                            else if ((result.contains("Error") || result.contains("Exception") || result.contains("SyntaxError")
                                    || result.contains("错误") || result.contains("失败"))&&toolDefinition.executor() == null) {
                                resultStatus = "failed";
                                result = "执行出错: " + result;
                            }
                            // 如果没有上述问题，resultStatus 默认为 success (上面已设置)

                        } catch (Exception e) {
                            result = "执行异常: " + e.getMessage();
                            resultStatus = "failed";
                        }
                        // --- 保存工具调用记录 (包含序号和状态) ---
                        // 注意参数顺序: conversationId, role, functionName, content, resultStatus
                        String toolResultId = saveMessage(
                                conversationId,
                                "assistant",
                                rawFunctionName,
                                "正在调用工具: " + rawFunctionName,
                                functionNameWithIndex,
                                resultStatus,
                                "tool_call",id,String.valueOf(i),String.format("{\"toolName\": \"%s\", \"arguments\": %s}", rawFunctionName, arguments), null, null
                                ,createdAt);

                        LocalDateTime now = LocalDateTime.now();
                        Integer secondsSinceCreation = (int) java.time.Duration.between(createdAt, now).getSeconds();
                        // --- 保存工具结果记录 ---
                        mId=saveMessage(
                                conversationId,
                                "assistant",
                                rawFunctionName,
                                result,
                                functionNameWithIndex,
                                resultStatus,
                                "tool_result", id,String.valueOf(i),String.format("{\"toolName\": \"%s\"}", rawFunctionName),toolResultId,secondsSinceCreation
                                ,LocalDateTime.now());

                        if (result == null || result.trim().isEmpty()) {
                            // --- 关键修复：工具返回空时，给一个默认值 ---
                            result = "工具执行成功，但未返回具体数据。";
                            resultStatus = "success";
                        }
                        // Add Tool Message (给 AI 的上下文 name 用原始名称)
                        messages.add(ChatCompletionMessage.builder()
                                .role("tool")
                                .toolCallId(callId)
                                .name(rawFunctionName)
                                .content(result)
                                .build());
                        messageDOList.add(ChatCompletionMessageDO.builder()
                                .role("tool")
                                .toolCallId(callId)
                                .name(rawFunctionName)
                                .content(result)
                                .conversationId(conversationId)
                                .createTime(new Date())
                                .build());
                    }
                } else {
                    finalResponse = message.getContent();
                    break;
                }
            }

            messageRepository.deleteById(resultId.get());
            // Save Assistant Message (最终回复没有 functionName)
            mId=saveMessage(conversationId, "assistant", "", finalResponse, "","success","result",id,"",null,null,null,LocalDateTime.now());

            // Send Response
            mId=saveMessage(conversationId, "assistant", "", finalResponse, "","success","response",id,"",null,null,null,LocalDateTime.now());

            mId=saveMessage(conversationId, "assistant", "", "", "","success","done",id,"",null,null,null,LocalDateTime.now());

            task.status = "completed";
            task.completedAt = LocalDateTime.now();
            moveToCompleted(taskId, task);
            // Save Assistant Message
            //saveMessage(conversationId, "assistant","all", finalResponse, "all", "success","3",id,"",null,null,null,LocalDateTime.now());

            return finalResponse;

        } catch (Exception e) {
            log.error("Error in sync agent task", e);
            task.status = "error";
            task.completedAt = LocalDateTime.now();
            moveToCompleted(taskId, task);
            throw new RuntimeException(e);
        }
    }

    public String executeTaskSync1(String conversationId, String messageText) {
        String taskId = UUID.randomUUID().toString();
        TaskInfo task = new TaskInfo();
        task.id = taskId;
        task.conversationId = conversationId;
        task.status = "running";
        task.message = messageText.length() > 50 ? messageText.substring(0, 50) + "..." : messageText;
        task.startedAt = LocalDateTime.now();
        runningTasks.put(taskId, task);

        try {
            // Save User Message
            String id = saveMessage(conversationId, "user","", messageText,"","","0","","",null,null,null,LocalDateTime.now());

            // Prepare Messages
            List<ChatCompletionMessage> messages = new ArrayList<>();
            messages.add(ChatCompletionMessage.builder().role("system").content(SYSTEM_PROMPT).build());
            messages.add(ChatCompletionMessage.builder().role("user").content(messageText).build());

            // Prepare Tools
            List<com.cyberstrike.service.openai.model.OpenAIModels.Tool> tools = new ArrayList<>();
            for (ToolRegistry.ToolDefinition def : toolRegistry.getTools()) {
                var function = new com.cyberstrike.service.openai.model.OpenAIModels.Function(
                        def.name(), def.description(), def.parameters());
                tools.add(new com.cyberstrike.service.openai.model.OpenAIModels.Tool("function", function));
            }

            int maxIterations = getMaxIterations();
            String finalResponse = "";

            // --- 1. 在 for 循环外部定义一个计数器 ---
            Map<String, Integer> functionCallCount = new HashMap<>();

            for (int i = 1; i <= maxIterations; i++) {
                if (task.cancelled) {
                    task.status = "cancelled";
                    task.completedAt = LocalDateTime.now();
                    moveToCompleted(taskId, task);
                    return "Task Cancelled";
                }

                ChatCompletionRequest aiRequest = ChatCompletionRequest.builder()
                        .model(getCurrentModel())
                        .messages(messages)
                        .tools(tools.isEmpty() ? null : tools)
                        .build();

                ChatCompletionResponse response = openAiService.chatCompletion(aiRequest);

                if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                    throw new RuntimeException("OpenAI returned no response");
                }

                ChatCompletionChoice choice = response.getChoices().get(0);
                ChatCompletionMessage message = choice.getMessage();
                messages.add(message);

                if ("tool_calls".equals(choice.getFinishReason()) && message.getToolCalls() != null) {
                    for (ToolCall toolCall : message.getToolCalls()) {
                        if (task.cancelled) {
                            task.status = "cancelled";
                            task.completedAt = LocalDateTime.now();
                            moveToCompleted(taskId, task);
                            return "Task Cancelled";
                        }

                        String rawFunctionName = toolCall.getFunction().getName();
                        String arguments = toolCall.getFunction().getArguments();
                        String callId = toolCall.getId();

                        // --- 2. 核心逻辑：更新计数器并生成带序号的名称 ---
                        // 获取当前函数名已出现的次数，如果第一次出现则默认为 0，然后 +1
                        int count = functionCallCount.getOrDefault(rawFunctionName, 0) + 1;
                        // 更新 Map，下次再遇到同名函数时计数会增加
                        functionCallCount.put(rawFunctionName, count);

                        // 拼接最终名称，例如：execute_script#1
                        String functionNameWithIndex = rawFunctionName + "#" + count;

                        // Execute Tool

                        String result ;
                        String resultStatus;

                        try {
                            result = toolRegistry.execute(rawFunctionName, arguments);
                            // 检查返回内容是否包含错误
                            if (result.contains("Error") || result.contains("Exception") || result.contains("SyntaxError")) {
                                resultStatus = "failed";
                                result = "执行出错: " + result;
                            } else {
                                resultStatus = "success";
                            }
                        } catch (Exception e) {
                            result = "执行异常: " + e.getMessage();
                            resultStatus = "failed";
                        }

                        // --- 保存工具调用记录 (包含序号和状态) ---
                        // 注意参数顺序: conversationId, role, functionName, content, resultStatus
                        saveMessage(
                                conversationId,
                                "assistant",
                                rawFunctionName,
                                String.format("调用工具: %s, 参数: %s", rawFunctionName, arguments),
                                functionNameWithIndex,
                                resultStatus,
                                "1",id,String.valueOf(i)
                                ,null,null,null,LocalDateTime.now());

                        // 如果执行成功，还要检查返回的结果内容，防止包含错误关键词
                        if (result.contains("Error") || result.contains("Exception") || result.contains("SyntaxError")) {
                            resultStatus = "fail";
                            // 可选：修改 result 内容，明确告知 AI 这是执行后的错误
                            result = "工具执行返回错误: " + result;
                        } else {
                            resultStatus = "success";
                        }

                        // --- 3. 保存记录 (使用带序号的名称) ---
                        // 注意：参数顺序根据你的代码调整为 (..., functionName, content, resultStatus)
                        // 请务必确认你的 saveMessage 方法参数定义是否匹配
                        saveMessage(
                                conversationId,
                                "assistant",
                                rawFunctionName,
                                String.format("调用工具: %s, 参数: %s", rawFunctionName, arguments), // 内容可以保留原始名或带序号
                                resultStatus,
                                functionNameWithIndex, // 使用带序号的名称存入数据库
                                "2",id,String.valueOf(i)
                                ,null,null,null,LocalDateTime.now());

                        // --- 4. Add Tool Message (给 AI 看的上下文通常用原始名称) ---
                        messages.add(ChatCompletionMessage.builder()
                                .role("tool")
                                .toolCallId(callId)
                                .name(rawFunctionName) // 这里通常传原始名给 AI
                                .content(result)
                                .build());
                    }
                } else {
                    finalResponse = message.getContent();
                    break;
                }
            }
            // Save Assistant Message
            saveMessage(conversationId, "assistant","all", finalResponse, "all", "success","3",id,"",null,null,null,LocalDateTime.now());

            task.status = "completed";
            task.completedAt = LocalDateTime.now();
            moveToCompleted(taskId, task);

            return finalResponse;

        } catch (Exception e) {
            log.error("Error in sync agent task", e);
            task.status = "error";
            task.completedAt = LocalDateTime.now();
            moveToCompleted(taskId, task);
            throw new RuntimeException(e);
        }
    }

    private void sendSseEvent(SseEmitter emitter, String type, String id, String message, String dataJson) throws IOException {
        String data = dataJson != null ? dataJson : "{}";
        String eventJson = String.format("{\"type\": \"%s\",\"id\": \"%s\", \"message\": \"%s\", \"data\": %s}",
                type,
                id,
                message.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", ""),
                data);

        emitter.send(SseEmitter.event().data(eventJson));
    }




//    private void sendSseEvent(SseEmitter emitter, String type, String message, Object dataObject) throws IOException {
//        try {
//            // --- 清洗阶段 (Defense) ---
//            // 1. 清洗 message: 移除常见的 AI 模型“幻觉”前缀和时间戳
//            String cleanedMessage = cleanString(message);
//
//            // 2. 如果 dataObject 是字符串，也进行清洗
//            //    (比如工具返回的纯文本结果可能包含脏字符)
//            Object processedData = dataObject;
//            if (dataObject instanceof String) {
//                processedData = cleanString((String) dataObject);
//            }
//            // 如果是 Map 或其他对象，我们不清洗键名，只清洗其中的字符串值
//            // 但为了性能和通用性，这里主要处理 String 情况。复杂的对象清洗交给 ObjectMapper 自动处理转义。
//
//            // --- 构建阶段 ---
//            Map<String, Object> eventMap = new HashMap<>();
//            eventMap.put("type", type);
//            eventMap.put("message", cleanedMessage);
//            eventMap.put("data", processedData != null ? processedData : Collections.emptyMap());
//
//            // --- 序列化阶段 ---
//            // ObjectMapper 会自动处理 JSON 转义（如 \n -> \\n, " -> \")
//            String jsonPayload = objectMapper.writeValueAsString(eventMap);
//
//            emitter.send(SseEmitter.event().data(jsonPayload));
//
//        } catch (Exception e) {
//            log.error("SSE Event send or clean error", e);
//            // 如果清洗或发送失败，尝试发送一个最简的错误事件
//            try {
//                Map<String, Object> errorMap = Map.of("type", "error", "message", "服务器内部错误", "data", "{}");
//                String errorJson = objectMapper.writeValueAsString(errorMap);
//                emitter.send(SseEmitter.event().data(errorJson));
//            } catch (IOException ex) {
//                emitter.completeWithError(ex);
//            }
//        }
//    }
//
//    // 清洗工具方法
//    private String cleanString(String input) {
//        if (input == null || input.isEmpty()) {
//            return input;
//        }
//
//        // 1. 移除 AI 模型常见的“复读”前缀
//        //    匹配类似 "ASSISTANT:", "AI:", "机器人:", "Response:", "输出:" 等
//        input = input.replaceAll("(?i)^(\\s*ASSISTANT\\s*[:：]?|\\s*AI\\s*[:：]?|\\s*Response\\s*[:：]?|\\s*输出\\s*[:：]?|\\s*机器人\\s*[:：]?|\\s*CyberStrikeAI\\s*[:：]?|\\s*Final\\s*Response\\s*[:：]?)", "");
//
//
//
//        // 3. 移除连续的特殊符号（如分隔线）
//        input = input.replaceAll("^\\s*[-=_*]{3,}\\s* $ ", "");
//
//        // 4. 清理多余的空白字符
//        input = input.trim();
//
//        // 5. 确保没有非法的控制字符（除了 \n, \t）
//        //    JSON 只允许特定的转义字符
//        //    这里简单处理：移除 ASCII 32 以下的控制字符（保留 \n 和 \t）
//        //    更严谨的做法是让 Jackson 处理，但提前清理更安全
//        //    如果上面的处理导致空字符串，保留原样（由 Jackson 抛异常）
//        return input;
//    }
}
