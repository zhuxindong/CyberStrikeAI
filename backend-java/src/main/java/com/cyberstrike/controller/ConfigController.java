package com.cyberstrike.controller;

import com.cyberstrike.tool.ToolRegistry;
import com.cyberstrike.tool.YamlToolLoader;
import com.cyberstrike.tool.YamlToolDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ToolRegistry toolRegistry;
    private final YamlToolLoader yamlToolLoader;

    // 内存配置存储 (实际应使用数据库)
    private Map<String, Object> config = new HashMap<>();
    // 工具启用状态记录
    private final Map<String, Boolean> toolEnabledStatus = new HashMap<>();

    public ConfigController(ToolRegistry toolRegistry, YamlToolLoader yamlToolLoader) {
        this.toolRegistry = toolRegistry;
        this.yamlToolLoader = yamlToolLoader;
        // 默认配置
        config.put("openai", Map.of(
                "apiKey", "",
                "baseUrl", "https://api.openai.com/v1",
                "model", "gpt-4o"));
        config.put("agent", Map.of(
                "maxIterations", 10));
        config.put("language", "zh-CN");
        config.put("theme", "dark");
    }

    @GetMapping
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(config);
    }

    @PutMapping
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> body) {
        config.putAll(body);
        return ResponseEntity.ok(config);
    }

    /**
     * 获取所有工具列表（包含启用状态）
     */
    @GetMapping("/tools")
    public ResponseEntity<?> getTools() {
        List<Map<String, Object>> toolList = new ArrayList<>();

        // 内置工具
        toolRegistry.getTools().forEach(tool -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", tool.name());
            map.put("description", tool.description());
            map.put("source", tool.executor() != null ? "builtin" : "yaml");
            map.put("enabled", toolEnabledStatus.getOrDefault(tool.name(), true));
            toolList.add(map);
        });

        return ResponseEntity.ok(Map.of("tools", toolList));
    }

    /**
     * 切换工具启用/禁用状态
     */
    @PostMapping("/tools/{name}/toggle")
    public ResponseEntity<?> toggleTool(@PathVariable String name, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 enabled 参数"));
        }

        // 更新内存状态
        toolEnabledStatus.put(name, enabled);

        // 如果是 YAML 工具，还需要更新 YamlToolLoader
        YamlToolDefinition yamlTool = yamlToolLoader.getTool(name);
        if (yamlTool != null) {
            yamlTool.setEnabled(enabled);
        }

        return ResponseEntity.ok(Map.of(
                "name", name,
                "enabled", enabled,
                "message", enabled ? "工具已启用" : "工具已禁用"));
    }

    /**
     * 刷新工具列表
     */
    @PostMapping("/tools/refresh")
    public ResponseEntity<?> refreshTools() {
        toolRegistry.refresh();
        return ResponseEntity.ok(Map.of(
                "message", "工具列表已刷新",
                "count", toolRegistry.getTools().size()));
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applyConfig() {
        // 应用配置的逻辑，比如重新加载工具等
        toolRegistry.refresh();
        return ResponseEntity.ok(Map.of("message", "配置已应用"));
    }
}
