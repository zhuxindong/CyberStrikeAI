package com.cyberstrike.controller;

import com.cyberstrike.entity.KnowledgeItem;
import com.cyberstrike.entity.RetrievalLog;
import com.cyberstrike.repository.KnowledgeItemRepository;
import com.cyberstrike.service.KnowledgeRetriever;
import com.cyberstrike.service.KnowledgeService;
import com.cyberstrike.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 【Go -> Java 迁移】知识库 REST API控制器
 * 对齐 Go 实现：internal/handler/knowledge.go
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeController.class);

    @Autowired
    private KnowledgeItemRepository repository;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private ToolRegistry toolRegistry;

    /**
     * 获取所有分类
     * GET /api/knowledge/categories
     * 对齐 Go：handler GetCategories
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        List<String> categories = knowledgeService.getCategories();
        return ResponseEntity.ok(Map.of("categories", categories));
    }

    /**
     * 获取知识项列表（支持按分类分页和关键字搜索）
     * GET /api/knowledge/items?category=&search=&limit=20&offset=0&categoryPage=true
     * 对齐 Go：handler GetItems
     */
    @GetMapping("/items")
    public ResponseEntity<Map<String, Object>> getItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean categoryPage) {

        // 限制每页最大数量
        if (limit > 500) {
            limit = 500;
        }

        // 如果提供了搜索关键字，执行关键字搜索
        if (search != null && !search.isEmpty()) {
            List<Map<String, Object>> items = knowledgeService.searchItemsByKeyword(search, category);

            // 按分类分组结果
            Map<String, List<Map<String, Object>>> groupedByCategory = new java.util.HashMap<>();
            for (Map<String, Object> item : items) {
                String cat = (String) item.getOrDefault("category", "未分类");
                if (cat == null || cat.isEmpty()) {
                    cat = "未分类";
                }
                groupedByCategory.computeIfAbsent(cat, k -> new java.util.ArrayList<>()).add(item);
            }

            // 转换为CategoryWithItems格式
            List<Map<String, Object>> categoriesWithItems = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByCategory.entrySet()) {
                Map<String, Object> categoryWithItems = new HashMap<>();
                categoryWithItems.put("category", entry.getKey());
                categoryWithItems.put("itemCount", entry.getValue().size());
                categoryWithItems.put("items", entry.getValue());
                categoriesWithItems.add(categoryWithItems);
            }

            // 按分类名称排序
            categoriesWithItems.sort(Comparator.comparing(m -> (String) m.get("category")));

            return ResponseEntity.ok(Map.of(
                    "categories", categoriesWithItems,
                    "total", categoriesWithItems.size(),
                    "search", search,
                    "is_search", true
            ));
        }

        // 分页模式处理
        if (categoryPage) {
            // 按分类分页模式（默认）- 与 Go 版本一致
            if (limit <= 0 || limit > 100) {
                limit = 10; // 默认每页10个分类
            }

            // 如果指定了category参数，则只返回该分类
            if (category != null && !category.isEmpty()) {
                // 单分类模式：返回该分类的所有知识项
                Map<String, Object> result = knowledgeService.getCategoryWithItems(category, limit, offset);
                return ResponseEntity.ok(result);
            }

            // 按分类分页模式 - 返回数据库中的知识项
            Map<String, Object> result = knowledgeService.getCategoriesWithItems(limit, offset);
            return ResponseEntity.ok(result);
        } else {
            // 按项分页模式（向后兼容）
            Map<String, Object> result = knowledgeService.getItemsWithOptions(category, limit, offset);
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取知识库内置工具列表
     * GET /api/knowledge/tools
     * 返回注册在ToolRegistry中的知识库工具
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> getKnowledgeTools() {
        // 从ToolRegistry获取知识工具
        var allTools = toolRegistry.getTools();

        // 过滤出知识类型的工具
        var knowledgeTools = allTools.stream()
                .filter(t -> "Knowledge".equals(t.toolType()))
                .map(t -> {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", t.name());
                    toolMap.put("description", t.description());
                    toolMap.put("parameters", t.parameters());
                    return toolMap;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "tools", knowledgeTools,
                "total", knowledgeTools.size()
        ));
    }

    /**
     * 获取单个知识项
     * GET /api/knowledge/items/{id}
     * 对齐 Go：handler GetItem
     */
    @GetMapping("/items/{id}")
    public ResponseEntity<?> getItem(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建知识项
     * POST /api/knowledge/items
     * 对齐 Go：handler CreateItem
     */
    @PostMapping("/items")
    public ResponseEntity<?> createItem(@RequestBody Map<String, String> request) {
        String category = request.get("category");
        String title = request.get("title");
        String content = request.get("content");

        if (category == null || category.isEmpty() || title == null || title.isEmpty() || content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的请求参数"));
        }

        try {
            KnowledgeItem item = knowledgeService.createItem(category, title, content);
            
            // 异步索引
            new Thread(() -> {
                try {
                    knowledgeService.indexItem(item.getId());
                    logger.info("知识项已创建并索引: {}", item.getId());
                } catch (Exception e) {
                    logger.warn("索引知识项失败: {}", item.getId(), e);
                }
            }).start();

            return ResponseEntity.ok(item);
        } catch (Exception e) {
            logger.error("创建知识项失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "创建知识项失败: " + e.getMessage()));
        }
    }

    /**
     * 更新知识项
     * PUT /api/knowledge/items/{id}
     * 对齐 Go：handler UpdateItem
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<?> updateItem(@PathVariable String id, @RequestBody Map<String, String> request) {
        String category = request.get("category");
        String title = request.get("title");
        String content = request.get("content");

        if (category == null || category.isEmpty() || title == null || title.isEmpty() || content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的请求参数"));
        }

        try {
            KnowledgeItem item = knowledgeService.updateItem(id, category, title, content);
            
            // 异步重新索引
            new Thread(() -> {
                try {
                    knowledgeService.indexItem(id);
                    logger.info("知识项已更新并重新索引: {}", id);
                } catch (Exception e) {
                    logger.warn("重新索引知识项失败: {}", id, e);
                }
            }).start();

            return ResponseEntity.ok(item);
        } catch (Exception e) {
            logger.error("更新知识项失败", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "更新知识项失败: " + e.getMessage()));
        }
    }

    /**
     * 删除知识项
     * DELETE /api/knowledge/items/{id}
     * 对齐 Go：handler DeleteItem
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable String id) {
        try {
            knowledgeService.deleteItem(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (Exception e) {
            logger.error("删除知识项失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除知识项失败: " + e.getMessage()));
        }
    }

    /**
     * 扫描知识库
     * POST /api/knowledge/scan
     * 对齐 Go：handler ScanKnowledgeBase
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanKnowledgeBase() {
        try {
            List<String> itemsToIndex = knowledgeService.scanKnowledgeBase();
            
            if (itemsToIndex.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "扫描完成，没有需要索引的新项或更新项"));
            }

            // 异步索引
            new Thread(() -> {
                logger.info("开始索引 {} 个新添加或更新的知识项", itemsToIndex.size());
                for (String itemId : itemsToIndex) {
                    try {
                        knowledgeService.indexItem(itemId);
                    } catch (Exception e) {
                        logger.warn("索引知识项失败: {}", itemId, e);
                    }
                }
            }).start();

            return ResponseEntity.ok(Map.of(
                "message", "扫描完成，开始索引 " + itemsToIndex.size() + " 个新添加或更新的知识项",
                "items_to_index", itemsToIndex.size()
            ));
        } catch (Exception e) {
            logger.error("扫描知识库失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "扫描知识库失败: " + e.getMessage()));
        }
    }

    /**
     * 重建索引
     * POST /api/knowledge/index
     * 对齐 Go：handler RebuildIndex
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        new Thread(() -> {
            knowledgeService.rebuildIndex();
        }).start();
        return ResponseEntity.ok(Map.of("message", "索引重建已开始，将在后台进行"));
    }

    /**
     * 获取索引状态
     * GET /api/knowledge/index/status
     * 对齐 Go：handler GetIndexStatus
     */
    @GetMapping("/index/status")
    public ResponseEntity<Map<String, Object>> getIndexStatus() {
        Map<String, Object> status = knowledgeService.getIndexStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 获取检索日志
     * GET /api/knowledge/retrieval-logs?conversationId=&messageId=&limit=50
     * 对齐 Go：handler GetRetrievalLogs
     */
    @GetMapping("/retrieval-logs")
    public ResponseEntity<Map<String, Object>> getRetrievalLogs(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String messageId,
            @RequestParam(defaultValue = "50") int limit) {
        
        if (limit > 100) {
            limit = 100;
        }

        List<RetrievalLog> logs = knowledgeService.getRetrievalLogs(conversationId, messageId, limit);
        return ResponseEntity.ok(Map.of("logs", logs));
    }

    /**
     * 删除检索日志
     * DELETE /api/knowledge/retrieval-logs/{id}
     * 对齐 Go：handler DeleteRetrievalLog
     */
    @DeleteMapping("/retrieval-logs/{id}")
    public ResponseEntity<?> deleteRetrievalLog(@PathVariable String id) {
        try {
            knowledgeService.deleteRetrievalLog(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (Exception e) {
            logger.error("删除检索日志失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除检索日志失败: " + e.getMessage()));
        }
    }

    /**
     * 获取知识库统计信息
     * GET /api/knowledge/stats
     * 对齐 Go：handler GetStats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = knowledgeService.getStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 搜索知识库（用于 API 调用）
     * POST /api/knowledge/search
     * 对齐 Go：handler Search
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        String riskType = (String) request.getOrDefault("riskType", "");
        int topK = request.containsKey("topK") ? (Integer) request.get("topK") : 5;
        double threshold = request.containsKey("threshold") ? ((Number) request.get("threshold")).doubleValue() : 0.7;

        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "查询不能为空"));
        }

        // 执行混合检索
        List<KnowledgeRetriever.RetrievalResult> results = knowledgeService.searchWithChunks(query, riskType, topK, threshold);
        
        // 转换为响应格式
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (KnowledgeRetriever.RetrievalResult result : results) {
            Map<String, Object> map = new HashMap<>();
            map.put("chunk", Map.of(
                "id", result.getChunk().getId(),
                "itemId", result.getChunk().getItemId(),
                "chunkIndex", result.getChunk().getChunkIndex(),
                "chunkText", result.getChunk().getChunkText()
            ));
            map.put("item", Map.of(
                "id", result.getItem().getId(),
                "category", result.getItem().getCategory(),
                "title", result.getItem().getTitle()
            ));
            map.put("similarity", result.getSimilarity());
            map.put("score", result.getScore());
            resultList.add(map);
        }

        return ResponseEntity.ok(Map.of("results", resultList));
    }

    /**
     * Search test endpoint (GET)
     * GET /api/knowledge/search?query=xxx
     */
    @GetMapping("/search")
    public List<Map<String, Object>> searchGet(@RequestParam String query) {
        List<KnowledgeRetriever.RetrievalResult> results = knowledgeService.searchWithChunks(query, null, 5, 0.7);
        
        return results.stream().map(result -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", result.getItem().getId());
            map.put("category", result.getItem().getCategory());
            map.put("title", result.getItem().getTitle());
            map.put("similarity", result.getSimilarity());
            map.put("score", result.getScore());
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }
}
