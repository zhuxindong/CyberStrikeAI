package com.cyberstrike.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * YAML 工具加载器和执行器
 * 支持从 tools/ 目录加载 YAML 工具定义，支持热加载
 */
@Component
public class YamlToolLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlToolLoader.class);

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final Map<String, YamlToolDefinition> tools = new ConcurrentHashMap<>();

    @Value("${security.tools-dir:tools}")
    private String toolsDir;

    @PostConstruct
    public void init() {
        loadAllTools();
    }

    /**
     * 加载所有 YAML 工具
     */
    public void loadAllTools() {
        tools.clear();

        Path toolsPath = Paths.get(toolsDir);
        if (!Files.exists(toolsPath)) {
            log.warn("工具目录不存在: {}", toolsDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(toolsPath, "*.yaml")) {
            for (Path file : stream) {
                try {
                    loadTool(file);
                } catch (Exception e) {
                    log.error("加载工具失败: {} - {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("读取工具目录失败: {}", e.getMessage());
        }

        log.info("加载了 {} 个 YAML 工具", tools.size());
    }

    /**
     * 加载单个工具
     */
    private void loadTool(Path file) throws IOException {
        YamlToolDefinition tool = yamlMapper.readValue(file.toFile(), YamlToolDefinition.class);
        if (tool.getName() != null && !tool.getName().isEmpty()) {
            tools.put(tool.getName(), tool);
            log.debug("加载工具: {}", tool.getName());
        }
    }

    /**
     * 获取所有启用的工具
     */
    public List<YamlToolDefinition> getEnabledTools() {
        List<YamlToolDefinition> enabled = new ArrayList<>();
        for (YamlToolDefinition tool : tools.values()) {
            if (tool.isEnabled()) {
                enabled.add(tool);
            }
        }
        return enabled;
    }

    /**
     * 获取工具
     */
    public YamlToolDefinition getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有工具
     */
    public Collection<YamlToolDefinition> getAllTools() {
        return tools.values();
    }

    /**
     * 执行工具
     */
    public String execute(String toolName, String argumentsJson) {
        YamlToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return "错误: 工具 '" + toolName + "' 不存在";
        }
        if (!tool.isEnabled()) {
            return "错误: 工具 '" + toolName + "' 已禁用";
        }

        try {
            List<String> command = buildCommand(tool, argumentsJson);
            log.info("执行命令: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误: 命令执行超时 (120秒)";
            }

            String result = output.toString();
            if (result.isEmpty()) {
                return "命令执行完成，无输出";
            }

            // 限制输出长度
            if (result.length() > 50000) {
                result = result.substring(0, 50000) + "\n... (输出过长，已截断)";
            }

            return result;

        } catch (Exception e) {
            log.error("执行工具失败: {} - {}", toolName, e.getMessage());
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 构建命令行
     */
    private List<String> buildCommand(YamlToolDefinition tool, String argumentsJson) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(tool.getCommand());

        // 添加固定参数
        if (tool.getArgs() != null) {
            command.addAll(tool.getArgs());
        }

        // 解析 JSON 参数
        @SuppressWarnings("unchecked")
        Map<String, Object> args = jsonMapper.readValue(argumentsJson, Map.class);

        // 按参数定义处理
        List<String> positionalArgs = new ArrayList<>();

        if (tool.getParameters() != null) {
            for (YamlToolDefinition.ToolParameter param : tool.getParameters()) {
                Object value = args.get(param.getName());

                // 使用默认值
                if (value == null && param.getDefaultValue() != null) {
                    value = param.getDefaultValue();
                }

                if (value == null)
                    continue;

                String format = param.getFormat();
                if (format == null)
                    format = "flag";

                switch (format) {
                    case "positional":
                        if (param.getPosition() != null) {
                            // 按位置插入
                            while (positionalArgs.size() <= param.getPosition()) {
                                positionalArgs.add(null);
                            }
                            positionalArgs.set(param.getPosition(), value.toString());
                        } else {
                            positionalArgs.add(value.toString());
                        }
                        break;

                    case "flag":
                        if (param.getFlag() != null) {
                            if ("bool".equals(param.getType()) || "boolean".equals(param.getType())) {
                                // 布尔参数只添加 flag
                                if (Boolean.TRUE.equals(value) || "true".equals(value.toString())) {
                                    command.add(param.getFlag());
                                }
                            } else {
                                command.add(param.getFlag());
                                command.add(value.toString());
                            }
                        }
                        break;

                    case "combined":
                        if (param.getFlag() != null) {
                            command.add(param.getFlag() + "=" + value);
                        }
                        break;

                    case "template":
                        String template = param.getTemplate();
                        if (template != null) {
                            String expanded = template.replace("{value}", value.toString());
                            // 模板可能包含多个参数，按空格分割
                            Collections.addAll(command, expanded.split("\\s+"));
                        }
                        break;
                }
            }
        }

        // 添加位置参数
        for (String arg : positionalArgs) {
            if (arg != null) {
                command.add(arg);
            }
        }

        return command;
    }

    /**
     * 刷新工具（热加载）
     */
    public void refresh() {
        loadAllTools();
    }

    /**
     * 设置工具启用状态
     */
    public void setEnabled(String toolName, boolean enabled) {
        YamlToolDefinition tool = tools.get(toolName);
        if (tool != null) {
            tool.setEnabled(enabled);
        }
    }
}
