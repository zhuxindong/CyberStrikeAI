package com.cyberstrike.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * SSE MCP 客户端实现
 * 通过 SSE (Server-Sent Events) 接收消息，通过 HTTP POST 发送请求
 */
public class SseMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(SseMcpClient.class);

    private final String url;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private ExecutorService sseExecutor;
    private volatile String status = "disconnected";
    private volatile List<McpTypes.Tool> cachedTools;
    private volatile boolean running = false;

    private final ConcurrentMap<String, CompletableFuture<McpTypes.Message>> pendingRequests = new ConcurrentHashMap<>();

    public SseMcpClient(String url, Duration timeout) {
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
        log.info("正在连接 SSE MCP 服务器: {}", url);

        try {
            // 启动 SSE 连接
            running = true;
            sseExecutor = Executors.newSingleThreadExecutor();
            sseExecutor.submit(this::readSseEvents);

            // 等待 SSE 连接建立
            Thread.sleep(500);

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
            log.info("SSE MCP 服务器连接成功: {}", url);
        } catch (Exception e) {
            status = "error";
            close();
            throw new McpException("初始化失败: " + e.getMessage(), e);
        }
    }

    private void readSseEvents() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.error("SSE 连接失败，状态码: {}", response.statusCode());
                status = "error";
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                StringBuilder eventData = new StringBuilder();
                String line;

                while (running && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // 空行表示事件结束
                        if (eventData.length() > 0) {
                            processEvent(eventData.toString());
                            eventData.setLength(0);
                        }
                    } else if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        eventData.append(data);
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                log.error("SSE 读取失败: {}", e.getMessage());
                status = "error";
            }
        }

        if (running) {
            status = "disconnected";
        }
    }

    private void processEvent(String data) {
        try {
            log.debug("收到 SSE 事件: {}", data);
            McpTypes.Message message = objectMapper.readValue(data, McpTypes.Message.class);

            // 匹配请求 ID
            String id = message.getId() != null ? message.getId().toString() : null;
            if (id != null) {
                CompletableFuture<McpTypes.Message> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(message);
                }
            }
        } catch (Exception e) {
            log.warn("解析 SSE 事件失败: {}", e.getMessage());
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
        String id = message.getId() != null ? message.getId().toString() : UUID.randomUUID().toString();
        message.setId(id);

        CompletableFuture<McpTypes.Message> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        try {
            String body = objectMapper.writeValueAsString(message);
            log.debug("发送 SSE 请求: {}", body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 202) {
                throw new McpException("HTTP 错误: " + response.statusCode() + " - " + response.body());
            }

            // 等待 SSE 响应
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(id);
            throw new McpException("请求超时");
        }
    }

    private void sendNotification(McpTypes.Message message) {
        try {
            String body = objectMapper.writeValueAsString(message);
            log.debug("发送 SSE 通知: {}", body);

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
        running = false;
        status = "disconnected";
        cachedTools = null;

        // 关闭 SSE 读取线程
        if (sseExecutor != null) {
            sseExecutor.shutdownNow();
        }

        // 清空待处理请求
        pendingRequests.forEach((id, future) -> future.cancel(true));
        pendingRequests.clear();

        log.info("SSE MCP 客户端已关闭");
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
