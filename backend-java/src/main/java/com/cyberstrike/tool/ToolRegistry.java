package com.cyberstrike.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
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
    private final com.cyberstrike.service.KnowledgeService knowledgeService;
    private final com.cyberstrike.service.PythonVenvService pythonVenvService;

    public ToolRegistry(YamlToolLoader yamlToolLoader,
            com.cyberstrike.service.KnowledgeService knowledgeService,
            com.cyberstrike.service.PythonVenvService pythonVenvService) {
        this.yamlToolLoader = yamlToolLoader;
        this.knowledgeService = knowledgeService;
        this.pythonVenvService = pythonVenvService;
        registerBuiltinTools();
        log.info("工具注册完成: {} 个内置工具, {} 个 YAML 工具",
                builtinTools.size(), yamlToolLoader.getAllTools().size());
    }

    /**
     * 注册内置工具（仅保留必要的基础工具）
     */
    private void registerBuiltinTools() {

        // ==================================================================
        // 核心修复：通用的命令执行函数 (解决 Windows 中文乱码)
        // 使用 GBK 编码读取流，防止 whois/nslookup/tracert 输出乱码
        // ==================================================================
        BiFunction<JsonNode, String, String> executeCommandWin = (args, command) -> {
            StringBuilder output = new StringBuilder();
            Process process = null;
            BufferedReader reader = null;

            try {
                // 1. 构建进程 (使用 cmd /c 执行命令)
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
                pb.redirectErrorStream(true); // 合并错误流和输出流
                process = pb.start();

                // 2. 核心修复：强制使用 GBK 编码读取 (Windows 控制台默认编码)
                // 如果是 Linux/Mac，这里应改为 UTF-8
                InputStreamReader isr = new InputStreamReader(process.getInputStream(), "GBK");
                reader = new BufferedReader(isr);

                // 3. 读取输出
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                // 4. 等待进程结束
                process.waitFor(10, TimeUnit.SECONDS); // 设置超时，防止挂起

            } catch (Exception e) {
                output.append("Command Execution Error: ").append(e.getMessage());
            } finally {
                // 关闭资源
                try { if (reader != null) reader.close(); } catch (IOException e) { /* 忽略 */ }
                if (process != null) process.destroy();
            }

            return output.toString();
        };

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

                    // 构建命令字符串
                    String cmd = "ping -n " + count + " " + target;

                    // 直接复用 executeCommandWin，无需重复写乱码修复逻辑
                    return executeCommandWin.apply(args, cmd);
                });


        // ==================================================================
        // 网络侦查工具 (使用上面修复乱码的函数)
        // ==================================================================

        // WHOIS 查询
        registerBuiltinTool("whois_lookup", "WHOIS 查询，获取域名注册信息",
                """
                {"type":"object", "properties":{
                    "domain":{"type":"string", "description":"要查询的域名，例如 example.com"}
                }, "required":["domain"]}
                """,
                (args) -> {
                    String domain = args.get("domain").asText();
                    // 直接拼接命令并执行
                    String cmd = "whois " + domain;
                    return executeCommandWin.apply(args, cmd);
                });

        // DNS 查询 (nslookup)
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
                    String cmd = "nslookup -type=" + type + " " + domain;
                    return executeCommandWin.apply(args, cmd);
                });

        // 路由追踪 (tracert)
        registerBuiltinTool("traceroute", "路由追踪",
                """
                {"type":"object", "properties":{
                    "target":{"type":"string", "description":"目标 IP 或域名"}
                }, "required":["target"]}
                """,
                (args) -> {
                    String target = args.get("target").asText();
                    String cmd = "tracert " + target;
                    return executeCommandWin.apply(args, cmd);
                });

        // HTTP 请求 (curl)
        // 注意：Curl 获取的网页通常是 UTF-8，但命令行提示符可能是 GBK
        // 这里为了防止命令错误提示乱码，依然使用 GBK 执行
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
                    StringBuilder cmd = new StringBuilder("curl -s -X ").append(method);

                    if (args.has("data")) {
                        cmd.append(" -d \"").append(args.get("data").asText()).append("\"");
                    }
                    cmd.append(" \"").append(url).append("\"");

                    return executeCommandWin.apply(args, cmd.toString());
                });

        // ==================================================================
        // Python 开发与执行工具
        // 注意：Python 的乱码修复需要在 pythonVenvService 内部实现
        // (确保其内部也是用 GBK 读取流，逻辑同上)
        // ==================================================================
        if (pythonVenvService != null) {
            registerBuiltinTool("install_python_package", "在虚拟环境中安装 Python 包",
                    """
                    {"type":"object", "properties":{
                        "package":{"type":"string", "description":"要安装的 Python 包名，如 requests, numpy"},
                        "env_name":{"type":"string", "description":"虚拟环境名称，默认 default"},
                        "additional_args":{"type":"string", "description":"额外的 pip 参数，如 --upgrade"}
                    }, "required":["package"]}
                    """,
                    (args) -> {
                        String pkg = args.get("package").asText();
                        String envName = args.has("env_name") ? args.get("env_name").asText() : "default";
                        String additionalArgs = args.has("additional_args") ? args.get("additional_args").asText() : "";
                        return pythonVenvService.installPackage(pkg, envName, additionalArgs);
                    });

            registerBuiltinTool("execute_python_script", "在虚拟环境中执行 Python 脚本",
                    """
                    {"type":"object", "properties":{
                        "script":{"type":"string", "description":"要执行的 Python 脚本内容"},
                        "env_name":{"type":"string", "description":"虚拟环境名称，默认 default"},
                        "additional_args":{"type":"string", "description":"额外的 Python 参数"}
                    }, "required":["script"]}
                    """,
                    (args) -> {
                        String script = args.get("script").asText();
                        String envName = args.has("env_name") ? args.get("env_name").asText() : "default";
                        String additionalArgs = args.has("additional_args") ? args.get("additional_args").asText() : "";
                        return pythonVenvService.executeScript(script, envName, additionalArgs);
                    });
        }

        // ==================================================================
        // 知识库检索
        // ==================================================================
        if (knowledgeService != null) {
            registerBuiltinTool("search_knowledge_base", "知识库向量检索，查询项目文档、经验库等",
                    """
                    {"type":"object", "properties":{
                        "query":{"type":"string", "description":"搜索关键词或自然语言问题"}
                    }, "required":["query"]}
                    """,
                    (args) -> {
                        String query = args.get("query").asText();
                        try {
                            List<com.cyberstrike.entity.KnowledgeItem> results = knowledgeService.search(query, 3);
                            if (results.isEmpty()) {
                                return "Knowledge Base: No relevant information found.";
                            }
                            StringBuilder sb = new StringBuilder("Knowledge Base Search Results:\n\n");
                            for (com.cyberstrike.entity.KnowledgeItem item : results) {
                                sb.append("--- [").append(item.getTitle()).append("] ---\n");
                                sb.append(item.getContent()).append("\n\n");
                            }
                            return sb.toString();
                        } catch (Exception e) {
                            return "Knowledge Base Error: " + e.getMessage();
                        }
                    });
        }

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

    public void registerBuiltinTool(String name, String description, String parametersJson,
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
