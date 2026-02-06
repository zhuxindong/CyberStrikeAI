package com.cyberstrike.mcp;

import com.cyberstrike.entity.McpServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class McpExecutor implements Function<JsonNode, String> {

    private final McpServer server;
    private final ObjectMapper objectMapper;

    // 本地缓存目录，用于存放下载下来的 heapdump 文件
    private static final Path DOWNLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "mcp_heapdumps");

    static {
        // 确保下载目录存在
        try {
            Files.createDirectories(DOWNLOAD_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public McpExecutor(McpServer server, ObjectMapper objectMapper) {
        this.server = server;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(JsonNode arguments) {
        try {
            String input = arguments.has("input") ? arguments.get("input").asText() : "";
            if ("stdio".equals(server.getTransport())) {
                return executeStdio(input);
            } else {
                return "Error: 不支持的传输协议";
            }
        } catch (Exception e) {
            return "执行出错: " + e.getMessage();
        }
    }

    private String executeStdio(String input) {
        Process process = null;
        Path tempHeapFile = null;

        try {
            // --- 1. 解析输入并下载文件 ---
            String localHeapPath = input;
            if (input != null && input.contains("http://")) {
                // 提取 URL
                String targetUrl = extractUrl(input);
                if (targetUrl == null) {
                    return "Error: 无法从输入中提取 URL";
                }
                System.out.println("正在下载 heapdump: " + targetUrl);
                tempHeapFile = downloadHeapdumpToFile(targetUrl);
                localHeapPath = tempHeapFile.toAbsolutePath().toString();
                System.out.println("下载完成，本地路径: " + localHeapPath);
            }

            // --- 2. 构建命令 ---
            List<String> commandList = new ArrayList<>();
            commandList.add(server.getCommand());
            if (server.getArgs() != null) {
                List<String> args = objectMapper.readValue(server.getArgs(), List.class);
                commandList.addAll(args);
            }

            ProcessBuilder pb = new ProcessBuilder(commandList);
            // 关键：不要合并错误流，否则日志会混入输出导致 JSON 解析失败
            // pb.redirectErrorStream(true);

            if (server.getEnv() != null) {
                Map<String, String> env = pb.environment();
                Map<String, String> envVars = objectMapper.readValue(server.getEnv(), Map.class);
                env.putAll(envVars);
            }

            process = pb.start();

            // --- 3. 启动日志消费者 ---
            // 单独开启线程读取 stderr，防止缓冲区堵塞，并将日志输出到 Java 控制台
            startLogConsumer(process.getErrorStream());

            // --- 4. 处理输入输出 ---
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                // 发送 initialize
                String initReq = "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"initialize\", \"params\": {}}";
                writer.write(initReq);
                writer.newLine();
                writer.flush();

                // 等待初始化完成（简单等待，不读取响应）
                Thread.sleep(500);

                // --- 5. 发送业务请求 ---
                ObjectNode paramsNode = objectMapper.createObjectNode();
                paramsNode.put("name", "analyze_heapdump");
                ObjectNode argsNode = objectMapper.createObjectNode();
                argsNode.put("path", localHeapPath);
                argsNode.put("timeout_sec", 120);
                paramsNode.set("arguments", argsNode);

                String callReq = String.format(
                        "{\"jsonrpc\": \"2.0\", \"id\": 2, \"method\": \"tools/call\", \"params\": %s}",
                        paramsNode.toString()
                );

                writer.write(callReq);
                writer.newLine();
                writer.flush();

                // --- 6. 读取结果 (精准版：跳过初始化响应，只取工具响应) ---
                StringBuilder fullResponse = new StringBuilder();
                int braceCount = 0;
                boolean messageReceived = false;
                long startTime = System.currentTimeMillis();

                while ((System.currentTimeMillis() - startTime) < 60000) {
                    if (reader.ready()) {
                        int ch = reader.read();
                        if (ch == -1) break;

                        char c = (char) ch;
                        fullResponse.append(c);

                        if (c == '{') {
                            braceCount++;
                        } else if (c == '}') {
                            braceCount--;
                            if (braceCount == 0) {
                                // 获取到一个完整的消息
                                String message = fullResponse.toString().trim();

                                try {
                                    JsonNode rootNode = objectMapper.readTree(message);

                                    // 关键逻辑：检查 id
                                    if (rootNode.has("id")) {
                                        int msgId = rootNode.get("id").asInt();

                                        if (msgId == 1) {
                                            // 这是 initialize 的响应，忽略它，继续等待下一个消息
                                            System.out.println("收到初始化响应，忽略...");
                                            fullResponse.setLength(0); // 清空缓冲区，准备接收下一条
                                            continue;
                                        } else if (msgId == 2) {
                                            // 这是我们要的工具调用结果
                                            messageReceived = true;
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    // 解析失败通常是因为还没读完，继续读
                                }
                            }
                        }
                    } else {
                        Thread.sleep(10);
                    }
                }

                if (!messageReceived) {
                    return "Error: 未收到预期的工具响应。\n最后收到的消息: " + fullResponse.toString();
                }

                // --- 7. 解析并返回结果 (绝对不报错版) ---
                String rawResult = fullResponse.toString().trim();

                // 关键：直接返回原始内容，不做任何 JSON 解析尝试
                // 无论它是 JSON、文本还是乱码，都直接返回
                return rawResult;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "执行失败: " + e.getMessage();
        } finally {
            // 清理临时文件
            if (tempHeapFile != null && Files.exists(tempHeapFile)) {
                try {
                    Files.deleteIfExists(tempHeapFile);
                } catch (IOException e) {
                    System.err.println("删除临时文件失败: " + tempHeapFile);
                }
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    // 提取字符串中的 URL
    private String extractUrl(String input) {
        if (input.contains("http://")) {
            int start = input.indexOf("http://");
            int end = input.length();
            if (input.indexOf(" ", start) > 0) {
                end = input.indexOf(" ", start);
            }
            return input.substring(start, end);
        }
        return null;
    }

    // 下载文件
    private Path downloadHeapdumpToFile(String urlString) throws IOException {
        URL url = new URL(urlString);
        Path tempFile = Files.createTempFile(DOWNLOAD_DIR, "heapdump_", ".hprof");

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
             FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        return tempFile;
    }

    // 异步消费日志流，防止 stderr 缓冲区填满导致死锁
    private void startLogConsumer(InputStream errorStream) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(errorStream))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    // 这里的输出仅用于调试，不会干扰主业务逻辑
                    System.err.println("[MCP-Server-Log] " + line);
                }
            } catch (IOException e) {
                // 连接断开通常意味着进程结束，可以忽略
            }
        });
    }
}