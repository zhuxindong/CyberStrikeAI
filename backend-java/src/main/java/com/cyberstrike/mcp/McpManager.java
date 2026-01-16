package com.cyberstrike.mcp;

import com.cyberstrike.entity.McpServer;
import com.cyberstrike.repository.McpServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP 客户端管理服务
 * 负责管理外部 MCP 服务器的连接和工具调用
 */
@Service
public class McpManager {

    private static final Logger log = LoggerFactory.getLogger(McpManager.class);

    private final McpServerRepository mcpServerRepository;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private final Map<String, String> errors = new ConcurrentHashMap<>();
    private final ExecutorService connectExecutor = Executors.newCachedThreadPool();

    public McpManager(McpServerRepository mcpServerRepository) {
        this.mcpServerRepository = mcpServerRepository;
    }

    /**
     * 创建 MCP 客户端
     */
    public McpClient createClient(McpServer server) {
        String transport = server.getTransport();
        Duration timeout = Duration.ofSeconds(30);

        return switch (transport) {
            case "http" -> new HttpMcpClient(server.getUrl(), timeout);
            case "sse" -> new SseMcpClient(server.getUrl(), timeout);
            case "stdio" -> {
                List<String> args = parseJsonArray(server.getArgs());
                Map<String, String> env = parseJsonObject(server.getEnv());
                yield new StdioMcpClient(server.getCommand(), args, env, timeout);
            }
            default -> throw new IllegalArgumentException("不支持的传输类型: " + transport);
        };
    }

    /**
     * 连接 MCP 服务器
     */
    public void connect(String serverId) throws McpException {
        Optional<McpServer> opt = mcpServerRepository.findById(serverId);
        if (opt.isEmpty()) {
            throw new McpException("服务器不存在: " + serverId);
        }

        McpServer server = opt.get();

        // 如果已有客户端，先关闭
        McpClient existing = clients.get(serverId);
        if (existing != null) {
            existing.close();
            clients.remove(serverId);
        }

        errors.remove(serverId);

        try {
            // 更新状态为 connecting
            server.setStatus("connecting");
            mcpServerRepository.save(server);

            // 创建并初始化客户端
            McpClient client = createClient(server);
            client.initialize();

            // 获取工具列表
            List<McpTypes.Tool> tools = client.listTools();

            // 更新服务器状态
            server.setStatus("connected");
            server.setToolCount(tools.size());
            server.setLastConnectedAt(LocalDateTime.now());
            mcpServerRepository.save(server);

            clients.put(serverId, client);
            log.info("MCP 服务器连接成功: {} ({} 个工具)", server.getName(), tools.size());
        } catch (Exception e) {
            server.setStatus("error");
            server.setToolCount(0);
            mcpServerRepository.save(server);
            errors.put(serverId, e.getMessage());
            throw new McpException("连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步连接 MCP 服务器
     */
    public void connectAsync(String serverId) {
        connectExecutor.submit(() -> {
            try {
                connect(serverId);
            } catch (Exception e) {
                log.error("异步连接 MCP 服务器失败: {}, {}", serverId, e.getMessage());
            }
        });
    }

    /**
     * 断开 MCP 服务器连接
     */
    public void disconnect(String serverId) {
        McpClient client = clients.remove(serverId);
        if (client != null) {
            client.close();
        }
        errors.remove(serverId);

        // 更新服务器状态
        mcpServerRepository.findById(serverId).ifPresent(server -> {
            server.setStatus("disconnected");
            server.setToolCount(0);
            mcpServerRepository.save(server);
        });

        log.info("MCP 服务器已断开: {}", serverId);
    }

    /**
     * 获取客户端
     */
    public McpClient getClient(String serverId) {
        return clients.get(serverId);
    }

    /**
     * 检查客户端是否已连接
     */
    public boolean isConnected(String serverId) {
        McpClient client = clients.get(serverId);
        return client != null && client.isConnected();
    }

    /**
     * 获取服务器状态
     */
    public String getStatus(String serverId) {
        McpClient client = clients.get(serverId);
        if (client != null) {
            return client.getStatus();
        }
        return mcpServerRepository.findById(serverId)
                .map(McpServer::getStatus)
                .orElse("unknown");
    }

    /**
     * 获取错误信息
     */
    public String getError(String serverId) {
        return errors.get(serverId);
    }

    /**
     * 获取工具数量
     */
    public int getToolCount(String serverId) {
        McpClient client = clients.get(serverId);
        return client != null ? client.getToolCount() : 0;
    }

    /**
     * 列出所有可用工具
     */
    public List<McpTypes.Tool> listTools(String serverId) throws McpException {
        McpClient client = clients.get(serverId);
        if (client == null || !client.isConnected()) {
            throw new McpException("客户端未连接");
        }
        return client.listTools();
    }

    /**
     * 调用工具
     */
    public McpTypes.ToolResult callTool(String serverId, String toolName, Map<String, Object> arguments)
            throws McpException {
        McpClient client = clients.get(serverId);
        if (client == null || !client.isConnected()) {
            throw new McpException("客户端未连接");
        }
        return client.callTool(toolName, arguments);
    }

    /**
     * 获取所有已连接的服务器 ID
     */
    public Set<String> getConnectedServerIds() {
        Set<String> connected = new HashSet<>();
        clients.forEach((id, client) -> {
            if (client.isConnected()) {
                connected.add(id);
            }
        });
        return connected;
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        long connectedCount = clients.values().stream()
                .filter(McpClient::isConnected)
                .count();

        int totalTools = clients.values().stream()
                .mapToInt(McpClient::getToolCount)
                .sum();

        stats.put("connectedServers", connectedCount);
        stats.put("totalExternalTools", totalTools);
        stats.put("totalServers", mcpServerRepository.count());

        return stats;
    }

    /**
     * 关闭所有连接
     */
    public void shutdown() {
        clients.values().forEach(McpClient::close);
        clients.clear();
        errors.clear();
        connectExecutor.shutdown();
        log.info("MCP Manager 已关闭");
    }

    // 辅助方法

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception e) {
            log.warn("解析 JSON 对象失败: {}", e.getMessage());
            return Map.of();
        }
    }
}
