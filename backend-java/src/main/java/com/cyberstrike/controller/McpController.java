package com.cyberstrike.controller;

import com.cyberstrike.entity.McpServer;
import com.cyberstrike.mcp.McpException;
import com.cyberstrike.mcp.McpManager;
import com.cyberstrike.mcp.McpTypes;
import com.cyberstrike.repository.McpServerRepository;
import com.cyberstrike.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 服务器管理控制器
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpServerRepository mcpServerRepository;
    private final ToolRegistry toolRegistry;
    private final McpManager mcpManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpController(McpServerRepository mcpServerRepository, ToolRegistry toolRegistry, McpManager mcpManager) {
        this.mcpServerRepository = mcpServerRepository;
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
}
