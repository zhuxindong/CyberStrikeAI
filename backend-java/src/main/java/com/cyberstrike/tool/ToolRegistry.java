package com.cyberstrike.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/**
 * 统一工具注册中心
 * 支持硬编码工具和 YAML 工具
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> builtinTools = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YamlToolLoader yamlToolLoader;

    public ToolRegistry(YamlToolLoader yamlToolLoader) {
        this.yamlToolLoader = yamlToolLoader;
        registerBuiltinTools();
        log.info("工具注册完成: {} 个内置工具, {} 个 YAML 工具",
                builtinTools.size(), yamlToolLoader.getAllTools().size());
    }

    /**
     * 注册内置工具（仅保留必要的基础工具）
     */
    private void registerBuiltinTools() {
        // 保留一些简单的基础工具作为后备
        registerBuiltinTool("ping_host", "Ping 目标主机检查可达性",
                """
                        {"type":"object", "properties":{
                            "target":{"type":"string", "description":"目标 IP 或域名"},
                            "count":{"type":"integer", "description":"Ping 次数，默认 4"}
                        }, "required":["target"]}
                        """,
                (args) -> {
                    String target = args.get("target").asText();
                    int count = args.has("count") ? args.get("count").asInt() : 4;
                    return executeCommand("ping -n " + count + " " + target);
                });

        registerBuiltinTool("whois_lookup", "WHOIS 查询，获取域名注册信息",
                """
                        {"type":"object", "properties":{
                            "domain":{"type":"string", "description":"要查询的域名"}
                        }, "required":["domain"]}
                        """,
                (args) -> {
                    String domain = args.get("domain").asText();
                    return executeCommand("whois " + domain);
                });

        registerBuiltinTool("dig_dns", "DNS 查询工具",
                """
                        {"type":"object", "properties":{
                            "domain":{"type":"string", "description":"要查询的域名"},
                            "type":{"type":"string", "description":"记录类型，如 A, MX, NS, TXT"}
                        }, "required":["domain"]}
                        """,
                (args) -> {
                    String domain = args.get("domain").asText();
                    String type = args.has("type") ? args.get("type").asText() : "A";
                    return executeCommand("nslookup -type=" + type + " " + domain);
                });

        registerBuiltinTool("curl_request", "发送 HTTP 请求",
                """
                        {"type":"object", "properties":{
                            "url":{"type":"string", "description":"目标 URL"},
                            "method":{"type":"string", "description":"HTTP 方法，默认 GET"},
                            "data":{"type":"string", "description":"POST 数据"}
                        }, "required":["url"]}
                        """,
                (args) -> {
                    String url = args.get("url").asText();
                    String method = args.has("method") ? args.get("method").asText() : "GET";
                    StringBuilder cmd = new StringBuilder("curl -s -X " + method);
                    if (args.has("data")) {
                        cmd.append(" -d \"").append(args.get("data").asText()).append("\"");
                    }
                    cmd.append(" \"").append(url).append("\"");
                    return executeCommand(cmd.toString());
                });

        registerBuiltinTool("traceroute", "路由追踪",
                """
                        {"type":"object", "properties":{
                            "target":{"type":"string", "description":"目标 IP 或域名"}
                        }, "required":["target"]}
                        """,
                (args) -> {
                    String target = args.get("target").asText();
                    return executeCommand("tracert " + target);
                });
    }

    /**
     * 获取所有工具定义（合并内置和 YAML）
     */
    public Collection<ToolDefinition> getTools() {
        List<ToolDefinition> allTools = new ArrayList<>(builtinTools.values());

        // 添加 YAML 工具
        for (YamlToolDefinition yamlTool : yamlToolLoader.getEnabledTools()) {
            // 跳过已存在的内置工具
            if (builtinTools.containsKey(yamlTool.getName())) {
                continue;
            }

            try {
                JsonNode params = objectMapper.valueToTree(yamlTool.toFunctionParameters());
                allTools.add(new ToolDefinition(
                        yamlTool.getName(),
                        yamlTool.getShortDescription() != null ? yamlTool.getShortDescription()
                                : yamlTool.getDescription(),
                        params,
                        null // YAML 工具使用 YamlToolLoader 执行
                ));
            } catch (Exception e) {
                log.error("转换 YAML 工具失败: {}", yamlTool.getName(), e);
            }
        }

        return allTools;
    }

    /**
     * 执行工具
     */
    public String execute(String name, String argumentsJson) {
        // 优先检查内置工具
        ToolDefinition builtin = builtinTools.get(name);
        if (builtin != null && builtin.executor != null) {
            try {
                JsonNode args = objectMapper.readTree(argumentsJson);
                log.info("执行内置工具: {} 参数: {}", name, argumentsJson);
                return builtin.executor.apply(args);
            } catch (Exception e) {
                log.error("执行内置工具失败: " + name, e);
                return "错误: " + e.getMessage();
            }
        }

        // 尝试 YAML 工具
        YamlToolDefinition yamlTool = yamlToolLoader.getTool(name);
        if (yamlTool != null) {
            log.info("执行 YAML 工具: {} 参数: {}", name, argumentsJson);
            return yamlToolLoader.execute(name, argumentsJson);
        }

        return "错误: 工具不存在: " + name;
    }

    /**
     * 获取工具
     */
    public ToolDefinition getTool(String name) {
        ToolDefinition builtin = builtinTools.get(name);
        if (builtin != null) {
            return builtin;
        }

        YamlToolDefinition yamlTool = yamlToolLoader.getTool(name);
        if (yamlTool != null) {
            try {
                JsonNode params = objectMapper.valueToTree(yamlTool.toFunctionParameters());
                return new ToolDefinition(
                        yamlTool.getName(),
                        yamlTool.getShortDescription(),
                        params,
                        null);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /**
     * 刷新 YAML 工具
     */
    public void refresh() {
        yamlToolLoader.refresh();
        log.info("工具刷新完成: {} 个 YAML 工具", yamlToolLoader.getAllTools().size());
    }

    /**
     * 执行系统命令
     */
    private String executeCommand(String command) {
        log.info("执行命令: {}", command);
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.warn("命令退出码 {}: {}", exitCode, command);
                }
                return output.toString();
            }
        } catch (Exception e) {
            log.error("命令执行失败: {}", command, e);
            return "错误: " + e.getMessage();
        }
    }

    private void registerBuiltinTool(String name, String description, String parametersJson,
            Function<JsonNode, String> executor) {
        try {
            JsonNode params = objectMapper.readTree(parametersJson);
            builtinTools.put(name, new ToolDefinition(name, description, params, executor));
        } catch (JsonProcessingException e) {
            log.error("解析工具参数失败: " + name, e);
        }
    }

    public record ToolDefinition(String name, String description, JsonNode parameters,
            Function<JsonNode, String> executor) {
    }
}
