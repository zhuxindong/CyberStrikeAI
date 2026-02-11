package com.cyberstrike.controller;

import com.cyberstrike.entity.McpServer;
import com.cyberstrike.entity.Message;
import com.cyberstrike.mcp.McpException;
import com.cyberstrike.mcp.McpManager;
import com.cyberstrike.mcp.McpTypes;
import com.cyberstrike.repository.McpServerRepository;
import com.cyberstrike.repository.MessageRepository;
import com.cyberstrike.tool.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 服务器管理控制器
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpServerRepository mcpServerRepository;
    private final MessageRepository messageRepository;
    private final ToolRegistry toolRegistry;
    private final McpManager mcpManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpController(McpServerRepository mcpServerRepository, MessageRepository messageRepository, ToolRegistry toolRegistry, McpManager mcpManager) {
        this.mcpServerRepository = mcpServerRepository;
        this.messageRepository = messageRepository;
        this.toolRegistry = toolRegistry;
        this.mcpManager = mcpManager;
    }

    /**
     * 获取所有内置工具列表
     */
    @GetMapping("/tools")
    public ResponseEntity<?> getBuiltinTools() {
        List<Map<String, Object>> tools = toolRegistry.getTools().stream()
                .map(tool -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", tool.name());
                    map.put("description", tool.description());
                    map.put("source", "builtin");
                    map.put("enabled", true);
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        result.put("total", tools.size());
        result.put("enabled", tools.size());
        result.put("disabled", 0);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有外部 MCP 服务器
     */
    @GetMapping("/servers")
    public ResponseEntity<?> listServers() {
        List<McpServer> servers = mcpServerRepository.findAllByOrderByCreatedAtDesc();

        // 更新实时状态
        servers.forEach(server -> {
            String realStatus = mcpManager.getStatus(server.getId());
            server.setStatus(realStatus);
            server.setToolCount(mcpManager.getToolCount(server.getId()));
        });

        return ResponseEntity.ok(servers);
    }

    /**
     * 获取单个 MCP 服务器
     */
    @GetMapping("/servers/{id}")
    public ResponseEntity<?> getServer(@PathVariable String id) {
        return mcpServerRepository.findById(id)
                .map(server -> {
                    server.setStatus(mcpManager.getStatus(id));
                    server.setToolCount(mcpManager.getToolCount(id));
                    return ResponseEntity.ok(server);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建 MCP 服务器
     */
    @PostMapping("/servers")
    @Transactional
    public ResponseEntity<?> createServer(@RequestBody Map<String, Map<String, Object>> jsonMap) {
        try {
            List<McpServer> result = new ArrayList<>();

            // 直接遍历 jsonMap
            for (Map.Entry<String, Map<String, Object>> entry : jsonMap.entrySet()) {
                String name = entry.getKey(); // "hexstrike-ai"
                Map<String, Object> config = entry.getValue(); // 内部的配置

                McpServer server = mcpServerRepository.findByName(name).orElse(new McpServer());
                boolean isNew = server.getId() == null;

                server.setName(name);
                server.setUpdatedAt(LocalDateTime.now());

                if (isNew) {
                    server.setId(UUID.randomUUID().toString());
                    server.setCreatedAt(LocalDateTime.now());
                    server.setStatus("disconnected");
                    server.setEnabled("true");
                    server.setToolCount(0);
                }

                server.setDescription((String) config.get("description"));

                // 优化：安全地设置 timeout (处理 null 情况)
                Object timeoutObj = config.get("timeout");
                if (timeoutObj instanceof Integer) {
                    server.setTimeout((Integer) timeoutObj);
                } else if (timeoutObj instanceof Number) {
                    // 兼容 JSON 解析出的 Double/Long 类型
                    server.setTimeout(((Number) timeoutObj).intValue());
                } else {
                    server.setTimeout(null);
                }

                // 新增：处理 tool_enabled 对象
                Object toolEnabledObj = config.get("tool_enabled");
                if (toolEnabledObj == null) {
                    toolEnabledObj = config.get("toolEnabled");
                }
                if (toolEnabledObj != null) {
                    // 将 Map/List 转为 JSON 字符串存入 env 字段
                    // 注意：这里复用 env 字段存储额外配置，或者你也可以专门建一个 metadata 字段
                    String toolEnabledJson = objectMapper.writeValueAsString(toolEnabledObj);
                    server.setToolEnabled(toolEnabledJson);
                } else {
                    // 如果没有传 tool_enabled，清空该字段（或者保留旧值，看需求）
                    // 这里选择清空，表示未配置
                    server.setToolEnabled(null);
                }
                // 处理 transport
                if (config.containsKey("transport")&&!"stdio".equalsIgnoreCase((String)config.get("transport"))) {
                    server.setTransport((String) config.get("transport"));
                    server.setUrl((String) config.get("url"));
                    server.setCommand(null);
                    server.setArgs(null);
                    server.setEnv(null);
                } else {
                    server.setTransport("stdio");
                    server.setCommand((String) config.get("command"));

                    // 将 List/Map 转为 JSON 字符串存入数据库
                    Object argsObj = config.get("args");
                    server.setArgs(argsObj != null ? objectMapper.writeValueAsString(argsObj) : null);

                    Object envObj = config.get("env");
                    server.setEnv(envObj != null ? objectMapper.writeValueAsString(envObj) : null);
                }

                mcpServerRepository.save(server);
                result.add(server);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("JSON 解析错误: " + e.getMessage());
        }
    }
//    @PostMapping("/servers")
//    public ResponseEntity<?> createServer(@RequestBody McpServer server) {
//        server.setId(UUID.randomUUID().toString());
//        server.setStatus("disconnected");
//        server.setToolCount(0);
//        server.setCreatedAt(LocalDateTime.now());
//        server.setUpdatedAt(LocalDateTime.now());
//
//        mcpServerRepository.save(server);
//        return ResponseEntity.ok(server);
//    }

    /**
     * 更新 MCP 服务器
     */
    @PutMapping("/servers/{id}")
    public ResponseEntity<?> updateServer(@PathVariable String id, @RequestBody McpServer body) {
        return mcpServerRepository.findById(id)
                .map(server -> {
                    if (body.getName() != null)
                        server.setName(body.getName());
                    if (body.getTransport() != null)
                        server.setTransport(body.getTransport());
                    if (body.getUrl() != null)
                        server.setUrl(body.getUrl());
                    if (body.getCommand() != null)
                        server.setCommand(body.getCommand());
                    if (body.getArgs() != null)
                        server.setArgs(body.getArgs());
                    if (body.getEnv() != null)
                        server.setEnv(body.getEnv());
                    server.setUpdatedAt(LocalDateTime.now());
                    mcpServerRepository.save(server);
                    return ResponseEntity.ok(server);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除 MCP 服务器
     */
    @DeleteMapping("/servers/{id}")
    public ResponseEntity<?> deleteServer(@PathVariable String id) {
        if (mcpServerRepository.existsById(id)) {
            // 先断开连接
            mcpManager.disconnect(id);
            mcpServerRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 连接 MCP 服务器
     */
    @PostMapping("/servers/{id}/connect")
    public ResponseEntity<?> connectServer(@PathVariable String id) {
        try {
            mcpManager.connect(id);
            McpServer server = mcpServerRepository.findById(id).orElse(null);
            return ResponseEntity.ok(Map.of(
                    "message", "连接成功",
                    "status", mcpManager.getStatus(id),
                    "toolCount", mcpManager.getToolCount(id),
                    "server", server));
        } catch (McpException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "status", "error"));
        }
    }

    /**
     * 断开 MCP 服务器
     */
    @PostMapping("/servers/{id}/disconnect")
    public ResponseEntity<?> disconnectServer(@PathVariable String id) {
        mcpManager.disconnect(id);
        return ResponseEntity.ok(Map.of("message", "已断开连接"));
    }

    /**
     * 获取 MCP 服务器的工具列表
     */
    @GetMapping("/servers/{id}/tools")
    public ResponseEntity<?> getServerTools(@PathVariable String id) {
        try {
            List<McpTypes.Tool> tools = mcpManager.listTools(id);
            return ResponseEntity.ok(Map.of("tools", tools));
        } catch (McpException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 调用 MCP 服务器的工具
     */
    @PostMapping("/servers/{id}/call")
    public ResponseEntity<?> callTool(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String toolName = (String) body.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) body.get("arguments");

        if (toolName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "工具名称不能为空"));
        }

        try {
            McpTypes.ToolResult result = mcpManager.callTool(id, toolName, arguments != null ? arguments : Map.of());
            return ResponseEntity.ok(result);
        } catch (McpException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 刷新工具列表
     */
    @PostMapping("/refresh-tools")
    public ResponseEntity<?> refreshTools() {
        // 刷新所有已连接服务器的工具列表
        Set<String> connectedIds = mcpManager.getConnectedServerIds();
        int refreshedCount = 0;
        int totalTools = 0;

        for (String serverId : connectedIds) {
            try {
                List<McpTypes.Tool> tools = mcpManager.listTools(serverId);
                totalTools += tools.size();
                refreshedCount++;
            } catch (McpException e) {
                // 忽略单个失败
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "工具列表已刷新",
                "refreshedServers", refreshedCount,
                "totalExternalTools", totalTools));
    }

    /**
     * 获取 MCP 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
//        Map<String, Object> stats = mcpManager.getStats();
//        stats.put("totalBuiltinTools", toolRegistry.getTools().size());
        Map<String, Object> stats =new HashMap<>();
        stats.put("status",mcpServerRepository.findByStatus("connected").size());
        stats.put("enabled",mcpServerRepository.findByEnabled("true").size());
        stats.put("disabled",mcpServerRepository.findByEnabled("flase").size());
        stats.put("total",mcpServerRepository.count());
        return ResponseEntity.ok(stats);
    }


    @GetMapping("/state")
    public ResponseEntity<?> getState() {
        List<Message> messageList = messageRepository.findAll();
        Map<String, Map<String, Object>> statsMap = new LinkedHashMap<>();

        // --- 核心修改：过滤 delFlag = 1 的数据 ---
        List<Message> toolMessages = messageList.stream()
                .filter(msg -> "tool_call".equals(msg.getType())
                        && msg.getDelFlag() != 1) // 保留 delFlag 不等于 1 的记录
                .collect(Collectors.toList());

        Map<String, List<Message>> groupedByTool = toolMessages.stream()
                .collect(Collectors.groupingBy(Message::getMcpExecutionIds));

        for (Map.Entry<String, List<Message>> entry : groupedByTool.entrySet()) {
            String toolName = entry.getKey();
            List<Message> messages = entry.getValue();

            int totalCalls = messages.size();
            int successCalls = (int) messages.stream().filter(m -> "success".equals(m.getResultStatus())).count();
            int failedCalls = totalCalls - successCalls;

            Optional<Message> lastCallOpt = messages.stream()
                    .max(Comparator.comparing(Message::getCreatedAt));

            // --- 核心修改：定义格式化器 ---
            String lastCallTime = lastCallOpt.map(message -> {
                ZonedDateTime zonedDateTime = message.getCreatedAt().atZone(java.time.ZoneId.systemDefault());
                // 使用你指定的格式 (注意: 24小时制用 HH)
                return zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }).orElse(null);

            Map<String, Object> toolStats = new HashMap<>();
            toolStats.put("toolName", toolName);
            toolStats.put("totalCalls", totalCalls);
            toolStats.put("successCalls", successCalls);
            toolStats.put("failedCalls", failedCalls);
            toolStats.put("lastCallTime", lastCallTime);

            statsMap.put(toolName, toolStats);
        }

        return ResponseEntity.ok(statsMap);
    }

    @GetMapping("/delete")
    @Transactional
    public ResponseEntity<?> delete(@RequestParam(required = false) List<String> id) {
        // 1. 校验参数
        if (id == null || id.isEmpty()) {
            return ResponseEntity.badRequest().body("ID 列表不能为空");
        }

        // 2. 执行批量删除
        // 返回值是总共删除的记录数
        int totalUpdated = messageRepository.deleteByToolIds(id);

        // 3. 返回结果
        return ResponseEntity.ok().body(Map.of(
                "message", "删除成功",
                "deletedCount", totalUpdated,
                "ids", id
        ));
    }

    /**
     * 获取 MCP 执行记录详情
     */
    @GetMapping("/executions")
    public ResponseEntity<?> executions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String status
    ) {
        page=page-1;
        // 1. 处理分页和排序
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. 处理查询参数
        String searchToolName = (toolName == null) ? "" : toolName;
        String searchStatus = (status == null) ? "" : status;

        // 3. 执行数据库查询
        Page<Message> callPage = messageRepository.findByTypeAndDelFlagNotAndMcpExecutionIdsContainingIgnoreCaseAndResultStatusContainingIgnoreCase(
                "tool_call", 1, searchToolName, searchStatus, pageable);

        List<Message> toolCalls = callPage.getContent();

        // 4. 准备响应结构 (包含 total)
        Map<String, Object> response = new HashMap<>();
        response.put("total", callPage.getTotalElements());

        // 如果没有数据，直接返回
        if (toolCalls.isEmpty()) {
            response.put("list", Collections.emptyList());
            return ResponseEntity.ok(response);
        }

        // 5. 提取 ID 关联结果
        List<String> callIds = toolCalls.stream().map(Message::getId).collect(Collectors.toList());
        List<Message> toolResults = messageRepository.findByToolIdInAndType(callIds, "tool_result");

        // 6. 转换为 Map 提高查找效率
        Map<String, Message> resultMap = toolResults.stream()
                .collect(Collectors.toMap(Message::getToolId, msg -> msg, (m1, m2) -> m1));

        // 7. 组装最终数据
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Message callMsg : toolCalls) {
            Message resultMsg = resultMap.get(callMsg.getId());

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", callMsg.getId());

            // ✅ 使用 mcpExecutionIds 作为工具名称
            record.put("toolName", callMsg.getMcpExecutionIds());

            // 状态逻辑
            String finalStatus = "running";
            if (resultMsg != null) {
                finalStatus = "success".equals(resultMsg.getResultStatus()) ? "success" : "failed";
            }
            record.put("status", finalStatus);

            // 解析参数
            record.put("arguments", parseJsonField(callMsg.getDataJson()));

            // 构建结果对象
            Map<String, Object> resultObj = buildResultContent(resultMsg);
            record.put("result", resultObj);
            record.put("error", resultMsg != null ? resultMsg.getContent() : "Execution in progress");

            // 时间处理 (格式: yyyy-MM-dd HH:mm:ss)
            String startTime = callMsg.getCreatedAt().atZone(ZoneId.systemDefault()).format(formatter);
            String endTime = startTime;

            // ⏱️ 耗时计算 (纳秒)
            long durationInNanos = 0;
            if (resultMsg != null && resultMsg.getCreatedAt() != null) {
                endTime = resultMsg.getCreatedAt().atZone(ZoneId.systemDefault()).format(formatter);
                durationInNanos = java.time.Duration.between(callMsg.getCreatedAt(), resultMsg.getCreatedAt()).toNanos();
            }

            record.put("startTime", startTime);
            record.put("endTime", endTime);
            record.put("duration", durationInNanos); // ✅ 单位: 纳秒

            result.add(record);
        }

        response.put("list", result);
        return ResponseEntity.ok(response);
    }

    // 辅助方法：解析 JSON 字段
    private Map<String, Object> parseJsonField(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            // 这里需要注入 ObjectMapper 或使用静态实例
            // return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return new HashMap<>();
        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("parse_error", e.getMessage());
            return errorMap;
        }
    }

    // 辅助方法：构建结果内容
    private Map<String, Object> buildResultContent(Message resultMsg) {
        Map<String, Object> resultObj = new HashMap<>();
        List<Map<String, String>> contentList = new ArrayList<>();

        Map<String, String> contentItem = new HashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", resultMsg != null ? resultMsg.getContent() : "...");

        contentList.add(contentItem);
        resultObj.put("content", contentList);
        resultObj.put("isError", resultMsg == null || !"success".equals(resultMsg.getResultStatus()));

        return resultObj;
    }
}

