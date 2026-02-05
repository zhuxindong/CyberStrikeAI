package com.cyberstrike.controller;

import com.cyberstrike.entity.Config;
import com.cyberstrike.repository.ConfigRepository;
import com.cyberstrike.tool.ToolRegistry;
import com.cyberstrike.tool.YamlToolLoader;
import com.cyberstrike.tool.YamlToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ToolRegistry toolRegistry;
    private final YamlToolLoader yamlToolLoader;

    @Autowired
    private ConfigRepository configRepository;

    // 内存配置存储 (实际应使用数据库)
//    private Map<String, Object> config = new HashMap<>();
    // 工具启用状态记录
    private final Map<String, Boolean> toolEnabledStatus = new HashMap<>();

    public ConfigController(ToolRegistry toolRegistry, YamlToolLoader yamlToolLoader) {
        this.toolRegistry = toolRegistry;
        this.yamlToolLoader = yamlToolLoader;
        // 默认配置
//        config.put("openai", Map.of(
//                "apiKey", "",
//                "baseUrl", "https://api.openai.com/v1",
//                "model", "gpt-4o"));
//        config.put("agent", Map.of(
//                "maxIterations", 10));
//        config.put("language", "zh-CN");
//        config.put("theme", "dark");
    }

    @GetMapping
    public ResponseEntity<?> getConfig() {
        java.util.Optional<Config> optionalConfig = configRepository.findById(1l);
        // 判断数据是否存在
        if (optionalConfig.isPresent()) {
            Config config = optionalConfig.get();
            return ResponseEntity.ok(config);
        }
        return null;
    }

    @PutMapping
    public ResponseEntity<?> updateConfig(@RequestBody Config config) {
        //config.putAll(body);
        config.setId(1l);
        configRepository.save(config);
        return ResponseEntity.ok(config);
    }

    /**
     * 获取所有工具列表（包含启用状态）
     */
//    @GetMapping("/tools")
//    public ResponseEntity<?> getTools() {
//        List<Map<String, Object>> toolList = new ArrayList<>();
//
//        // 内置工具
//        toolRegistry.getTools().forEach(tool -> {
//            Map<String, Object> map = new HashMap<>();
//            map.put("name", tool.name());
//            map.put("description", tool.description());
//            map.put("source", tool.executor() != null ? "builtin" : "yaml");
//            map.put("enabled", toolEnabledStatus.getOrDefault(tool.name(), true));
//            toolList.add(map);
//        });
//
//        return ResponseEntity.ok(Map.of("tools", toolList));
//    }

    @GetMapping("/tools")
    public ResponseEntity<?> getTools(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int page_size,
            @RequestParam(required = false) String search) {

        // 1. 获取所有工具（为了计算总数和搜索）
        List<YamlToolDefinition> allTools = new ArrayList<>(yamlToolLoader.getAllTools());

        // 2. 如果有搜索关键词，进行过滤
        List<YamlToolDefinition> filteredTools = allTools;
        if (search != null && !search.trim().isEmpty()) {
            String lowerSearch = search.toLowerCase().trim();
            filteredTools = allTools.stream()
                    .filter(tool -> {
                        // 在 name 或 description 里搜索
                        boolean matchName = tool.getName() != null && tool.getName().toLowerCase().contains(lowerSearch);
                        boolean matchDesc = tool.getDescription() != null && tool.getDescription().toLowerCase().contains(lowerSearch);
                        return matchName || matchDesc;
                    })
                    .collect(Collectors.toList());
        }

        // 3. 计算总数
        int total = filteredTools.size();

        // 4. 进行分页 (跳过 + 限制)
        // 注意：如果 page_size 是 -1 或者类似值表示“全部”，可以加个判断
        List<Map<String, Object>> pagedTools = filteredTools.stream()
                .skip(page * page_size-1) // 跳过前面的页
                .limit(page_size)      // 只取这一页的数量
                .map(tool -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", tool.getName());
                    map.put("description", tool.getDescription());
                    map.put("enabled", tool.isEnabled());
                    return map;
                })
                .collect(Collectors.toList());

        // 5. 计算总页数
        int totalPages = page_size > 0 ? (int) Math.ceil((double) total / page_size) : 0;

        // 6. 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("tools", pagedTools);
        result.put("total", total);
        result.put("total_enabled", (int) filteredTools.stream().filter(YamlToolDefinition::isEnabled).count()); // 额外统计启用的数量
        result.put("page", page);
        result.put("page_size", page_size);
        result.put("total_page", totalPages);

        return ResponseEntity.ok(result);
    }

//    /**
//     * 切换工具启用/禁用状态
//     */
//    @PostMapping("/tools/{name}/toggle")
//    public ResponseEntity<?> toggleTool(@PathVariable String name, @RequestBody Map<String, Boolean> body) {
//        Boolean enabled = body.get("enabled");
//        if (enabled == null) {
//            return ResponseEntity.badRequest().body(Map.of("error", "缺少 enabled 参数"));
//        }
//
//        // 更新内存状态
//        toolEnabledStatus.put(name, enabled);
//
//        // 如果是 YAML 工具，还需要更新 YamlToolLoader
//        YamlToolDefinition yamlTool = yamlToolLoader.getTool(name);
//        if (yamlTool != null) {
//            yamlTool.setEnabled(enabled);
//        }
//
//        return ResponseEntity.ok(Map.of(
//                "name", name,
//                "enabled", enabled,
//                "message", enabled ? "工具已启用" : "工具已禁用"));
//    }

    /**
     * 切换工具启用/禁用状态
     */
    @PostMapping("/tools/update")
    public ResponseEntity<?> toggleTool(@RequestBody List<Map<String, Object>> list) throws IOException {
        for (Map<String, Object> map: list){
            Boolean enabled = (Boolean) map.get("enabled");
            if (enabled == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "缺少 enabled 参数"));
            }
            // 更新内存状态
            toolEnabledStatus.put((String) map.get("name"), enabled);
            // 如果是 YAML 工具，还需要更新 YamlToolLoader
            YamlToolDefinition yamlTool = yamlToolLoader.getTool((String) map.get("name"));
            if (yamlTool != null) {
                yamlTool.setEnabled(enabled);
            }
            // 2. 写入文件
            yamlToolLoader.updateEnabledInYamlFile((String) map.get("name"),enabled);
        }
        return ResponseEntity.ok("工具配置已成功保存！");
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
