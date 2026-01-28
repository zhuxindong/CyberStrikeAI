package com.cyberstrike.service;

import com.cyberstrike.dto.ChatRequest;
import com.cyberstrike.entity.Config;
import com.cyberstrike.entity.Conversation;
import com.cyberstrike.entity.Message;
import com.cyberstrike.repository.ConfigRepository;
import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.MessageRepository;
import com.cyberstrike.service.openai.OpenAiService;
import com.cyberstrike.service.openai.model.OpenAIModels.*;
import com.cyberstrike.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AgentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentService.class);

    private final OpenAiService openAiService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
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
            ToolRegistry toolRegistry,
            ConfigRepository configRepository) {
        this.openAiService = openAiService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
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

        executor.submit(() -> {
            try {

                // Save User Message (注意：这里传入了空字符串占位 functionName 和 resultStatus)
                String id = saveMessage(conversationId, "user", "", request.getMessage(), "", "", "user","",null,null);

                // 发送任务 ID
                sendSseEvent(emitter, "conversation", "任务已开始",
                        String.format("{\"taskId\": \"%s\", \"conversationId\": \"%s\"}", taskId, conversationId));


                // Prepare Messages
                List<ChatCompletionMessage> messages = new ArrayList<>();
                messages.add(ChatCompletionMessage.builder().role("system").content(SYSTEM_PROMPT).build());
                messages.add(ChatCompletionMessage.builder().role("user").content(request.getMessage()).build());

                // Prepare Tools
                List<com.cyberstrike.service.openai.model.OpenAIModels.Tool> tools = new ArrayList<>();
                for (ToolRegistry.ToolDefinition def : toolRegistry.getTools()) {
                    var function = new com.cyberstrike.service.openai.model.OpenAIModels.Function(
                            def.name(), def.description(), def.parameters());
                    tools.add(new com.cyberstrike.service.openai.model.OpenAIModels.Tool("function", function));
                }

                int maxIterations = getMaxIterations();

                String finalResponse = "";

                // --- 定义计数器 Map ---
                Map<String, Integer> functionCallCount = new HashMap<>();

                for (int i = 1; i <= maxIterations; i++) {
                    // 检查是否被取消
                    if (task.cancelled) {
                        sendSseEvent(emitter, "cancelled", "任务已被用户取消，后续操作已停止。", null);
                        saveMessage(conversationId, "assistant", "", "任务已被用户取消，后续操作已停止。", "","success","cancelled",id,"",null);
                        task.status = "cancelled";
                        task.completedAt = LocalDateTime.now();
                        moveToCompleted(taskId, task);
                        emitter.complete();
                        return;
                    }
                    sendSseEvent(emitter, "iteration", "开始分析请求并制定测试策略",String.format("{\"iteration\": \"%s\"}", i));

                    saveMessage(conversationId, "assistant", "", "开始分析请求并制定测试策略", "","success","iteration",id,String.valueOf(i),null);

                    sendSseEvent(emitter, "progress", "正在调用AI模型...", null);

                    saveMessage(conversationId, "assistant", "", "正在调用AI模型...", "","success","progress",id,String.valueOf(i),null);


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

                    if (message.getContent() != null) {
                        sendSseEvent(emitter, "thinking", message.getContent(), null);
                        saveMessage(conversationId, "assistant", "", message.getContent(), "","success","thinking",id,String.valueOf(i),null);
                    }

                    if ("tool_calls".equals(choice.getFinishReason()) && message.getToolCalls() != null) {

                        for (ToolCall toolCall : message.getToolCalls()) {
                            // 检查取消
                            if (task.cancelled) {
                                sendSseEvent(emitter, "cancelled", "任务已被用户取消，后续操作已停止。", null);
                                saveMessage(conversationId, "assistant", "", "任务已被用户取消，后续操作已停止。", "","success","cancelled",id,"",null);

                                task.status = "cancelled";
                                task.completedAt = LocalDateTime.now();
                                moveToCompleted(taskId, task);
                                emitter.complete();
                                return;
                            }
                            sendSseEvent(emitter, "tool_calls_detected", "检测到 1 个工具调用",null);
                            saveMessage(conversationId, "assistant", "", "检测到 1 个工具调用", "","success","tool_calls_detected",id,String.valueOf(i),null);


                            String rawFunctionName = toolCall.getFunction().getName();
                            String arguments = toolCall.getFunction().getArguments();
                            String callId = toolCall.getId();

                            // --- 生成带序号的名称 ---
                            int count = functionCallCount.getOrDefault(rawFunctionName, 0) + 1;
                            functionCallCount.put(rawFunctionName, count);
                            String functionNameWithIndex = rawFunctionName + "#" + count;

                            // --- 执行工具并捕获结果 ---
                            String result;
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
                                    "正在调用工具: " + rawFunctionName,
                                    functionNameWithIndex,
                                    resultStatus,
                                    "tool_call",id,String.valueOf(i),String.format("{\"toolName\": \"%s\", \"arguments\": %s}", rawFunctionName, arguments)
                            );

                            sendSseEvent(emitter, "tool_call", "正在调用工具: " + rawFunctionName,
                                    String.format("{\"toolName\": \"%s\", \"arguments\": %s}", rawFunctionName, arguments));

                            // --- 保存工具结果记录 ---
                            saveMessage(
                                    conversationId,
                                    "assistant",
                                    rawFunctionName,
                                    result,
                                    functionNameWithIndex,
                                    resultStatus,
                                    "tool_result", id,String.valueOf(i),String.format("{\"toolName\": \"%s\"}", rawFunctionName)
                            );

                            sendSseEvent(emitter, "tool_result", result,
                                    String.format("{\"toolName\": \"%s\"}", rawFunctionName));

                            // Add Tool Message (给 AI 的上下文 name 用原始名称)
                            messages.add(ChatCompletionMessage.builder()
                                    .role("tool")
                                    .toolCallId(callId)
                                    .name(rawFunctionName)
                                    .content(result)
                                    .build());
                        }
                    } else {
                        finalResponse = message.getContent();
                        break;
                    }
                }

                // Save Assistant Message (最终回复没有 functionName)
                saveMessage(conversationId, "assistant", "", finalResponse, "","success","result",id,"",null);

                // Send Response
                sendSseEvent(emitter, "response", finalResponse, "{\"conversationId\": \"" + conversationId + "\"}");
                saveMessage(conversationId, "assistant", "", finalResponse, "","success","response",id,"",null);

                sendSseEvent(emitter, "done", "", null);
                saveMessage(conversationId, "assistant", "", "", "","success","done",id,"",null);

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
                    sendSseEvent(emitter, "error", "执行出错: " + e.getMessage(), null);
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        return emitter;
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
                               String type,String requestId,String iteration,String dataJson) {
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

        try {
            // Save User Message
            String id = saveMessage(conversationId, "user","", messageText,"","","0","","",null);

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
                                ,null);

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
                                ,null);

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
            saveMessage(conversationId, "assistant","all", finalResponse, "all", "success","3",id,"",null);

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

    private void sendSseEvent(SseEmitter emitter, String type, String message, String dataJson) throws IOException {
        String data = dataJson != null ? dataJson : "{}";
        String eventJson = String.format("{\"type\": \"%s\", \"message\": \"%s\", \"data\": %s}",
                type,
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
