package com.cyberstrike.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP MCP 客户端实现
 * 通过 HTTP POST 发送 JSON-RPC 请求
 */
public class HttpMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpClient.class);

    private final String url;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String status = "disconnected";
    private volatile List<McpTypes.Tool> cachedTools;

    public HttpMcpClient(String url, Duration timeout) {
        this.url = url;
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void initialize() throws McpException {
        status = "connecting";
        log.info("正在连接 HTTP MCP 服务器: {}", url);

        try {
            // 发送初始化请求
            McpTypes.Message request = new McpTypes.Message();
            request.setId("1");
            request.setMethod("initialize");
            request.setParams(new McpTypes.InitializeRequest());

            McpTypes.Message response = sendRequest(request);
            if (response.getError() != null) {
                throw new McpException(response.getError().getCode(), response.getError().getMessage());
            }

            // 发送 initialized 通知
            McpTypes.Message notification = new McpTypes.Message();
            notification.setMethod("notifications/initialized");
            notification.setParams(Map.of());
            sendNotification(notification);

            status = "connected";
            log.info("HTTP MCP 服务器连接成功: {}", url);
        } catch (Exception e) {
            status = "error";
            throw new McpException("初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<McpTypes.Tool> listTools() throws McpException {
        if (!isConnected()) {
            throw new McpException("客户端未连接");
        }

        try {
            McpTypes.Message request = new McpTypes.Message();
            request.setId(UUID.randomUUID().toString());
            request.setMethod("tools/list");
            request.setParams(Map.of());

            McpTypes.Message response = sendRequest(request);
            if (response.getError() != null) {
                throw new McpException(response.getError().getCode(), response.getError().getMessage());
            }

            McpTypes.ListToolsResponse toolsResponse = objectMapper.convertValue(
                    response.getResult(), McpTypes.ListToolsResponse.class);

            cachedTools = toolsResponse.getTools();
            return cachedTools;
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("获取工具列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public McpTypes.ToolResult callTool(String name, Map<String, Object> arguments) throws McpException {
        if (!isConnected()) {
            throw new McpException("客户端未连接");
        }

        try {
            McpTypes.Message request = new McpTypes.Message();
            request.setId(UUID.randomUUID().toString());
            request.setMethod("tools/call");
            request.setParams(new McpTypes.CallToolRequest(name, arguments));

            McpTypes.Message response = sendRequest(request);
            if (response.getError() != null) {
                throw new McpException(response.getError().getCode(), response.getError().getMessage());
            }

            McpTypes.CallToolResponse callResponse = objectMapper.convertValue(
                    response.getResult(), McpTypes.CallToolResponse.class);

            return new McpTypes.ToolResult(callResponse.getContent(), callResponse.isIsError());
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("调用工具失败: " + e.getMessage(), e);
        }
    }

    private McpTypes.Message sendRequest(McpTypes.Message message) throws Exception {
        String body = objectMapper.writeValueAsString(message);
        log.debug("发送 MCP 请求: {}", body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new McpException("HTTP 错误: " + response.statusCode() + " - " + response.body());
        }

        log.debug("收到 MCP 响应: {}", response.body());
        return objectMapper.readValue(response.body(), McpTypes.Message.class);
    }

    private void sendNotification(McpTypes.Message message) {
        try {
            String body = objectMapper.writeValueAsString(message);
            log.debug("发送 MCP 通知: {}", body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("发送通知失败: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        status = "disconnected";
        cachedTools = null;
        log.info("HTTP MCP 客户端已关闭");
    }

    @Override
    public boolean isConnected() {
        return "connected".equals(status);
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public int getToolCount() {
        return cachedTools != null ? cachedTools.size() : 0;
    }
}
