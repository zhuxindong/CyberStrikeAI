package com.cyberstrike.mcp;

import com.cyberstrike.entity.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP 工具的执行器
 * 负责根据 McpServer 的配置，实际执行外部命令或 HTTP 请求
 */
public class McpExecutor implements Function<JsonNode, String> {

    private final McpServer server;
    private final ObjectMapper objectMapper;

    public McpExecutor(McpServer server, ObjectMapper objectMapper) {
        this.server = server;
        this.objectMapper = objectMapper;
    }

    @Override
    public String apply(JsonNode arguments) {
        try {
            // 1. 提取 AI 传入的参数
            // 假设我们定义的参数只有一个 "input"
            String input = arguments.has("input") ? arguments.get("input").asText() : "";

            // 2. 根据 Transport 类型执行
            if ("stdio".equals(server.getTransport())) {
                return executeStdio(input);
            } else if ("http".equals(server.getTransport()) || "sse".equals(server.getTransport())) {
                return executeHttp(input);
            } else {
                return "ERROR: 不支持的传输协议: " + server.getTransport();
            }

        } catch (Exception e) {
            return "执行出错: " + e.getMessage();
        }
    }

    private String executeStdio(String input) {
        try {
            // 1. 解析命令和参数
            List<String> commandList = new ArrayList<>();
            commandList.add(server.getCommand());

            // 这里需要把 input 填入到 args 中
            // 简单处理：把 input 作为最后一个参数
            // 或者你可以解析 server.getArgs() (JSON Array) 并替换占位符
            if (server.getArgs() != null) {
                List<String> args = objectMapper.readValue(server.getArgs(), List.class);
                commandList.addAll(args);
            }
            commandList.add(input); // 把 AI 的输入作为参数传入

            // 2. 执行进程 (这里只是示意，你需要用你的进程管理器)
            ProcessBuilder pb = new ProcessBuilder(commandList);
            // 设置环境变量
            if (server.getEnv() != null) {
                Map<String, String> env = pb.environment();
                Map<String, String> envVars = objectMapper.readValue(server.getEnv(), Map.class);
                env.putAll(envVars);
            }

            Process process = pb.start();
            // ... 读取输出流和错误流 ...
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();

            return "ExitCode: " + exitCode + "\nOutput: " + output;

        } catch (Exception e) {
            return "Stdio执行失败: " + e.getMessage();
        }
    }

    private String executeHttp(String input) {
        try {
            // 这里调用你的 HttpClient
            // 把 input 放入请求体或 URL 参数中
            // return httpClient.post(server.getUrl(), input);
            return "HTTP调用未实现: " + server.getUrl();
        } catch (Exception e) {
            return "HTTP调用失败: " + e.getMessage();
        }
    }

    // 简单的流读取辅助方法
    private String readProcessOutput(Process process) throws IOException {
        // 实际使用中请使用 Apache Commons IOUtils 或类似工具
        return new String(process.getInputStream().readAllBytes());
    }
}