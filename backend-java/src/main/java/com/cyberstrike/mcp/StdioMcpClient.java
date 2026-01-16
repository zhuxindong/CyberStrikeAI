package com.cyberstrike.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * STDIO MCP 客户端实现
 * 通过子进程的 stdin/stdout 通信
 */
public class StdioMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpClient.class);

    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private ExecutorService readExecutor;
    private volatile String status = "disconnected";
    private volatile List<McpTypes.Tool> cachedTools;

    private final AtomicLong requestIdCounter = new AtomicLong(0);
    private final ConcurrentMap<String, CompletableFuture<McpTypes.Message>> pendingRequests = new ConcurrentHashMap<>();

    public StdioMcpClient(String command, List<String> args, Map<String, String> env, Duration timeout) {
        this.command = command;
        this.args = args != null ? args : List.of();
        this.env = env != null ? env : Map.of();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void initialize() throws McpException {
        status = "connecting";
        log.info("正在启动 STDIO MCP 进程: {} {}", command, String.join(" ", args));

        try {
            // 构建进程命令
            List<String> cmdList = new ArrayList<>();
            cmdList.add(command);
            cmdList.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(false);

            // 设置环境变量
            Map<String, String> processEnv = pb.environment();
            processEnv.putAll(env);

            process = pb.start();
            stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // 启动响应读取线程
            readExecutor = Executors.newSingleThreadExecutor();
            readExecutor.submit(this::readResponses);

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
            log.info("STDIO MCP 进程启动成功: {}", command);
        } catch (Exception e) {
            status = "error";
            close();
            throw new McpException("启动进程失败: " + e.getMessage(), e);
        }
    }

    private void readResponses() {
        try {
            String line;
            while ((line = stdout.readLine()) != null) {
                try {
                    log.debug("收到 STDIO 响应: {}", line);
                    McpTypes.Message message = objectMapper.readValue(line, McpTypes.Message.class);

                    // 匹配请求 ID
                    String id = message.getId() != null ? message.getId().toString() : null;
                    if (id != null) {
                        CompletableFuture<McpTypes.Message> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(message);
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析响应失败: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            if (!"disconnected".equals(status)) {
                log.error("读取响应失败: {}", e.getMessage());
                status = "error";
            }
        }
        status = "disconnected";
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
        String id = message.getId() != null ? message.getId().toString()
                : String.valueOf(requestIdCounter.incrementAndGet());
        message.setId(id);

        CompletableFuture<McpTypes.Message> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        try {
            String body = objectMapper.writeValueAsString(message);
            log.debug("发送 STDIO 请求: {}", body);

            synchronized (stdin) {
                stdin.write(body);
                stdin.newLine();
                stdin.flush();
            }

            // 等待响应
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(id);
            throw new McpException("请求超时");
        }
    }

    private void sendNotification(McpTypes.Message message) {
        try {
            String body = objectMapper.writeValueAsString(message);
            log.debug("发送 STDIO 通知: {}", body);

            synchronized (stdin) {
                stdin.write(body);
                stdin.newLine();
                stdin.flush();
            }
        } catch (Exception e) {
            log.warn("发送通知失败: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        status = "disconnected";
        cachedTools = null;

        // 关闭流
        try {
            if (stdin != null) {
                stdin.close();
            }
        } catch (Exception e) {
            log.debug("关闭 stdin 失败: {}", e.getMessage());
        }

        try {
            if (stdout != null) {
                stdout.close();
            }
        } catch (Exception e) {
            log.debug("关闭 stdout 失败: {}", e.getMessage());
        }

        // 终止进程
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 关闭读取线程
        if (readExecutor != null) {
            readExecutor.shutdownNow();
        }

        // 清空待处理请求
        pendingRequests.forEach((id, future) -> future.cancel(true));
        pendingRequests.clear();

        log.info("STDIO MCP 客户端已关闭");
    }

    @Override
    public boolean isConnected() {
        return "connected".equals(status) && process != null && process.isAlive();
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
