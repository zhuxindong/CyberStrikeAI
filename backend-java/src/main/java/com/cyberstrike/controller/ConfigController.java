package com.cyberstrike.controller;

import com.cyberstrike.tool.ToolRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ToolRegistry toolRegistry;

    // 内存配置存储 (实际应使用数据库)
    private Map<String, Object> config = new HashMap<>();

    public ConfigController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        // 默认配置
        config.put("language", "zh-CN");
        config.put("theme", "dark");
        config.put("maxIterations", 10);
        config.put("timeout", 300);
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

    @GetMapping("/tools")
    public ResponseEntity<?> getTools() {
        List<Map<String, Object>> toolList = toolRegistry.getTools().stream()
                .map(tool -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", tool.name());
                    map.put("description", tool.description());
                    map.put("parameters", tool.parameters());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(toolList);
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applyConfig() {
        // 应用配置的逻辑，比如重新加载工具等
        return ResponseEntity.ok(Map.of("message", "配置已应用"));
    }
}
