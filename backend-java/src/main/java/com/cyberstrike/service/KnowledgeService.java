package com.cyberstrike.service;

import com.cyberstrike.entity.KnowledgeItem;
import com.cyberstrike.entity.RetrievalLog;
import com.cyberstrike.repository.KnowledgeItemRepository;
import com.cyberstrike.repository.RetrievalLogRepository;
import com.cyberstrike.tool.ToolContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 【Go -> Java 迁移】知识库服务
 * 对齐 Go 实现：internal/knowledge/manager.go Manager
 */
@Service
public class KnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeItemRepository repository;
    private final RetrievalLogRepository retrievalLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.knowledge-dir:knowledge_base}")
    private String knowledgeBasePath;

    @Autowired
    private KnowledgeIndexer knowledgeIndexer;

    @Autowired
    private KnowledgeRetriever knowledgeRetriever;

    public KnowledgeService(KnowledgeItemRepository repository, 
                           RetrievalLogRepository retrievalLogRepository) {
        this.repository = repository;
        this.retrievalLogRepository = retrievalLogRepository;
    }

    /**
     * 记录知识检索统计（类似 SkillsStatsService.recordSkillCall）
     * 在 builtinTools.put 注册的工具执行时调用
     */
    public void recordKnowledgeRetrieval(String query, boolean success) {
        try {
            // 从 ToolContext 获取当前 conversationId 和 messageId
            String conversationId = ToolContext.getConversationId();
            String messageId = ToolContext.getMessageId();
            
            // 记录检索日志
            RetrievalLog log = new RetrievalLog();
            log.setId(UUID.randomUUID().toString());
            log.setConversationId(conversationId);
            log.setMessageId(messageId);
            log.setQuery(query);
            log.setRetrievedItems(success ? "retrieved" : "failed");
            log.setCreatedAt(LocalDateTime.now());
            retrievalLogRepository.save(log);
            
            logger.debug("记录知识检索统计: conversationId={}, messageId={}, query={}, success={}", 
                    conversationId, messageId, query, success);
        } catch (Exception e) {
            logger.warn("记录知识检索统计失败: {}", e.getMessage());
        }
    }

    /**
     * 获取知识库目录路径
     */
    public String getKnowledgeBasePath() {
        return knowledgeBasePath;
    }

    /**
     * 设置知识库目录路径
     */
    public void setKnowledgeBasePath(String knowledgeBasePath) {
        this.knowledgeBasePath = knowledgeBasePath;
    }

    /**
     * 应用启动后自动扫描知识库并建立索引
     * 对齐 Go：app.go 中的异步扫描逻辑
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("应用启动完成，开始自动扫描知识库...");
        try {
            // 扫描知识库
            List<String> itemsToIndex = scanKnowledgeBase();
            logger.info("知识库扫描完成，待索引项数量: {}", itemsToIndex.size());

            if (itemsToIndex.isEmpty()) {
                logger.info("知识库无需索引");
                return;
            }

            // 检查索引状态
            boolean hasIndex = knowledgeIndexer.hasIndex();
            if (hasIndex) {
                // 增量索引
                logger.info("检测到已有知识库索引，开始增量索引...");
                for (String itemId : itemsToIndex) {
                    try {
                        knowledgeIndexer.indexItem(itemId);
                    } catch (Exception e) {
                        logger.warn("增量索引知识项失败: {}", itemId, e);
                    }
                }
                logger.info("增量索引完成");
            } else {
                // 重建索引
                logger.info("未检测到索引，开始全量索引...");
                knowledgeIndexer.rebuildIndex();
                logger.info("全量索引完成");
            }
        } catch (Exception e) {
            logger.error("自动扫描知识库失败", e);
        }
    }

    /**
     * 扫描知识库目录，更新数据库
     * 对齐 Go：manager.ScanKnowledgeBase
     */
    @Transactional
    public List<String> scanKnowledgeBase() throws IOException {
        if (knowledgeBasePath == null || knowledgeBasePath.isEmpty()) {
            throw new IOException("知识库路径未配置");
        }

        // 确保目录存在
        Path basePath = Paths.get(knowledgeBasePath);
        if (!Files.exists(basePath)) {
            Files.createDirectories(basePath);
        }

        List<String> itemsToIndex = new ArrayList<>();

        // 遍历知识库目录
        Files.walk(basePath, Integer.MAX_VALUE).forEach(path -> {
            if (!Files.isRegularFile(path)) {
                return;
            }

            String pathStr = path.toString();
            if (!pathStr.toLowerCase().endsWith(".md")) {
                return;
            }

            try {
                // 计算相对路径和分类
                Path relativePath = basePath.relativize(path);
                // 对 File.separator 进行正则表达式转义
                String[] parts = relativePath.toString().split(Pattern.quote(File.separator));
                String category = parts.length > 1 ? parts[0] : "未分类";
                String title = path.getFileName().toString().replaceAll("\\.md$", "");

                // 读取文件内容
                String content = new String(Files.readAllBytes(path));

                // 检查是否已存在
                Optional<KnowledgeItem> existing = repository.findByTitleAndCategory(title, category);

                if (existing.isEmpty()) {
                    // 创建新项
                    String id = UUID.randomUUID().toString();
                    KnowledgeItem item = new KnowledgeItem();
                    item.setId(id);
                    item.setCategory(category);
                    item.setTitle(title);
                    item.setFilePath(pathStr);
                    item.setContent(content);
                    item.setCreatedAt(LocalDateTime.now());
                    item.setUpdatedAt(LocalDateTime.now());
                    repository.save(item);
                    logger.info("添加知识项: {} - {}", category, title);
                    itemsToIndex.add(id);
                } else {
                    KnowledgeItem item = existing.get();
                    if (!item.getContent().equals(content)) {
                        // 更新现有项
                        item.setContent(content);
                        item.setFilePath(pathStr);
                        item.setUpdatedAt(LocalDateTime.now());
                        repository.save(item);
                        logger.info("更新知识项: {} - {}", category, title);
                        itemsToIndex.add(item.getId());
                    }
                }
            } catch (IOException e) {
                logger.warn("处理知识库文件失败: {} - {}", path, e.getMessage());
            }
        });

        return itemsToIndex;
    }

    /**
     * 获取所有分类
     * 对齐 Go：manager.GetCategories
     */
    public List<String> getCategories() {
        return repository.findDistinctCategories();
    }

    /**
     * 获取知识库统计信息
     * 对齐 Go：manager.GetStats
     */
    public Map<String, Object> getStats() {
        List<String> categories = getCategories();
        int totalCategories = categories.size();
        long totalItems = repository.countAll();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_categories", totalCategories);
        stats.put("total_items", totalItems);
        
        return stats;
    }

    /**
     * 按分类分页获取知识项
     * 对齐 Go：manager.GetCategoriesWithItems
     */
    public Map<String, Object> getCategoriesWithItems(int limit, int offset) {
        List<String> categories = getCategories();
        int totalCategories = categories.size();

        // 应用分页
        List<String> paginatedCategories;
        if (limit > 0) {
            int start = offset;
            int end = Math.min(offset + limit, totalCategories);
            if (start >= totalCategories) {
                paginatedCategories = Collections.emptyList();
            } else {
                paginatedCategories = categories.subList(start, end);
            }
        } else {
            paginatedCategories = categories;
        }

        List<Map<String, Object>> categoriesWithItems = new ArrayList<>();
        for (String category : paginatedCategories) {
            List<KnowledgeItem> items = repository.findByCategoryOrderByTitle(category);
            List<Map<String, Object>> itemSummaries = items.stream().map(item -> {
                Map<String, Object> summary = new HashMap<>();
                summary.put("id", item.getId());
                summary.put("category", item.getCategory());
                summary.put("title", item.getTitle());
                summary.put("filePath", item.getFilePath());
                summary.put("createdAt", item.getCreatedAt());
                summary.put("updatedAt", item.getUpdatedAt());
                return summary;
            }).collect(Collectors.toList());

            Map<String, Object> categoryWithItems = new HashMap<>();
            categoryWithItems.put("category", category);
            categoryWithItems.put("itemCount", items.size());
            categoryWithItems.put("items", itemSummaries);
            categoriesWithItems.add(categoryWithItems);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("categories", categoriesWithItems);
        result.put("total", totalCategories);
        result.put("limit", limit);
        result.put("offset", offset);

        return result;
    }

    /**
     * 获取指定分类的知识项（单分类模式）
     * 对齐 Go：manager.GetItemsSummary
     */
    public Map<String, Object> getCategoryWithItems(String category, int limit, int offset) {
        List<KnowledgeItem> items = repository.findByCategoryOrderByTitle(category);
        int total = items.size();

        // 应用分页
        List<KnowledgeItem> paginatedItems;
        if (limit > 0) {
            int start = offset;
            int end = Math.min(offset + limit, total);
            if (start >= total) {
                paginatedItems = Collections.emptyList();
            } else {
                paginatedItems = items.subList(start, end);
            }
        } else {
            paginatedItems = items;
        }

        List<Map<String, Object>> itemSummaries = paginatedItems.stream().map(item -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", item.getId());
            summary.put("category", item.getCategory());
            summary.put("title", item.getTitle());
            summary.put("filePath", item.getFilePath());
            summary.put("createdAt", item.getCreatedAt());
            summary.put("updatedAt", item.getUpdatedAt());
            return summary;
        }).collect(Collectors.toList());

        // 包装成单分类结构
        List<Map<String, Object>> categoriesWithItems = new ArrayList<>();
        Map<String, Object> categoryWithItems = new HashMap<>();
        categoryWithItems.put("category", category);
        categoryWithItems.put("itemCount", total);
        categoryWithItems.put("items", itemSummaries);
        categoriesWithItems.add(categoryWithItems);

        Map<String, Object> result = new HashMap<>();
        result.put("categories", categoriesWithItems);
        result.put("total", 1); // 只有一个分类
        result.put("limit", limit);
        result.put("offset", offset);

        return result;
    }

    /**
     * 按项分页模式获取知识项（向后兼容）
     * 对齐 Go：manager.GetItemsWithOptions
     */
    public Map<String, Object> getItemsWithOptions(String category, int limit, int offset) {
        List<KnowledgeItem> items;
        if (category != null && !category.isEmpty()) {
            items = repository.findByCategoryOrderByTitle(category);
        } else {
            items = repository.findAllByOrderByUpdatedAtDesc();
        }

        int total = items.size();

        // 应用分页
        List<KnowledgeItem> paginatedItems;
        if (limit > 0) {
            int start = offset;
            int end = Math.min(offset + limit, total);
            if (start >= total) {
                paginatedItems = Collections.emptyList();
            } else {
                paginatedItems = items.subList(start, end);
            }
        } else {
            paginatedItems = items;
        }

        List<Map<String, Object>> itemSummaries = paginatedItems.stream().map(item -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", item.getId());
            summary.put("category", item.getCategory());
            summary.put("title", item.getTitle());
            summary.put("filePath", item.getFilePath());
            summary.put("createdAt", item.getCreatedAt());
            summary.put("updatedAt", item.getUpdatedAt());
            return summary;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", itemSummaries);
        result.put("total", total);
        result.put("limit", limit);
        result.put("offset", offset);

        return result;
    }

    /**
     * 获取知识项摘要列表（不包含完整内容，支持分页）
     * 对齐 Go：manager.GetItemsSummary
     */
    public Map<String, Object> getItemsSummary(String category, int limit, int offset) {
        List<KnowledgeItem> items;
        if (category != null && !category.isEmpty()) {
            items = repository.findByCategoryOrderByTitle(category);
        } else {
            items = repository.findAllByOrderByUpdatedAtDesc();
        }

        int total = items.size();

        // 应用分页
        List<KnowledgeItem> paginatedItems;
        if (limit > 0) {
            int start = offset;
            int end = Math.min(offset + limit, total);
            if (start >= total) {
                paginatedItems = Collections.emptyList();
            } else {
                paginatedItems = items.subList(start, end);
            }
        } else {
            paginatedItems = items;
        }

        List<Map<String, Object>> itemSummaries = paginatedItems.stream().map(item -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", item.getId());
            summary.put("category", item.getCategory());
            summary.put("title", item.getTitle());
            summary.put("filePath", item.getFilePath());
            summary.put("createdAt", item.getCreatedAt());
            summary.put("updatedAt", item.getUpdatedAt());
            return summary;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", itemSummaries);
        result.put("total", total);
        result.put("limit", limit);
        result.put("offset", offset);
        
        return result;
    }

    /**
     * 按关键字搜索知识项
     * 对齐 Go：manager.SearchItemsByKeyword
     */
    public List<Map<String, Object>> searchItemsByKeyword(String keyword, String category) {
        List<KnowledgeItem> items;
        if (keyword != null && !keyword.isEmpty()) {
            items = repository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(keyword, keyword);
        } else {
            items = repository.findAll();
        }

        // 如果指定了分类，添加分类过滤
        if (category != null && !category.isEmpty()) {
            items = items.stream()
                    .filter(item -> category.equalsIgnoreCase(item.getCategory()))
                    .collect(Collectors.toList());
        }

        return items.stream().map(item -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", item.getId());
            summary.put("category", item.getCategory());
            summary.put("title", item.getTitle());
            summary.put("filePath", item.getFilePath());
            summary.put("createdAt", item.getCreatedAt());
            summary.put("updatedAt", item.getUpdatedAt());
            return summary;
        }).collect(Collectors.toList());
    }

    /**
     * 获取单个知识项
     * 对齐 Go：manager.GetItem
     */
    public Optional<KnowledgeItem> getItem(String id) {
        return repository.findById(id);
    }

    /**
     * 创建知识项
     * 对齐 Go：manager.CreateItem
     */
    @Transactional
    public KnowledgeItem createItem(String category, String title, String content) throws IOException {
        // 构建文件路径
        Path filePath = Paths.get(knowledgeBasePath, category, title + ".md");
        
        // 确保目录存在
        Files.createDirectories(filePath.getParent());
        
        // 写入文件
        Files.write(filePath, content.getBytes());

        // 插入数据库
        KnowledgeItem item = new KnowledgeItem();
        item.setId(UUID.randomUUID().toString());
        item.setCategory(category);
        item.setTitle(title);
        item.setFilePath(filePath.toString());
        item.setContent(content);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        
        return repository.save(item);
    }

    /**
     * 更新知识项
     * 对齐 Go：manager.UpdateItem
     */
    @Transactional
    public KnowledgeItem updateItem(String id, String category, String title, String content) throws IOException {
        KnowledgeItem item = repository.findById(id)
                .orElseThrow(() -> new IOException("知识项不存在"));

        // 构建新文件路径
        Path newFilePath = Paths.get(knowledgeBasePath, category, title + ".md");
        
        // 如果路径改变，需要移动文件
        Path oldFilePath = Paths.get(knowledgeBasePath, item.getCategory(), item.getTitle() + ".md");
        if (!oldFilePath.equals(newFilePath)) {
            Files.createDirectories(newFilePath.getParent());
            Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 写入文件
        Files.write(newFilePath, content.getBytes());

        // 更新数据库
        item.setCategory(category);
        item.setTitle(title);
        item.setFilePath(newFilePath.toString());
        item.setContent(content);
        item.setUpdatedAt(LocalDateTime.now());
        
        return repository.save(item);
    }

    /**
     * 删除知识项
     * 对齐 Go：manager.DeleteItem
     */
    @Transactional
    public void deleteItem(String id) throws IOException {
        KnowledgeItem item = repository.findById(id)
                .orElseThrow(() -> new IOException("知识项不存在"));

        // 删除文件
        Path filePath = Paths.get(knowledgeBasePath, item.getCategory(), item.getTitle() + ".md");
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }

        // 删除数据库记录
        repository.delete(item);
    }

    /**
     * 记录检索日志
     * 对齐 Go：manager.LogRetrieval
     */
    @Transactional
    public void logRetrieval(String conversationId, String messageId, String query, String riskType, List<String> retrievedItems) {
        RetrievalLog log = new RetrievalLog();
        log.setId(UUID.randomUUID().toString());
        log.setConversationId(conversationId);
        log.setMessageId(messageId);
        log.setQuery(query);
        log.setRiskType(riskType);
        
        try {
            log.setRetrievedItems(objectMapper.writeValueAsString(retrievedItems));
        } catch (JsonProcessingException e) {
            log.setRetrievedItems("[]");
        }
        
        log.setCreatedAt(LocalDateTime.now());
        retrievalLogRepository.save(log);
    }

    /**
     * 获取检索日志
     * 对齐 Go：manager.GetRetrievalLogs
     */
    public List<RetrievalLog> getRetrievalLogs(String conversationId, String messageId, int limit) {
        if (messageId != null && !messageId.isEmpty()) {
            return retrievalLogRepository.findByMessageIdOrderByCreatedAtDesc(messageId);
        } else if (conversationId != null && !conversationId.isEmpty()) {
            return retrievalLogRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        } else {
            return retrievalLogRepository.findTopByOrderByCreatedAtDesc(limit);
        }
    }

    /**
     * 删除检索日志
     * 对齐 Go：manager.DeleteRetrievalLog
     */
    @Transactional
    public void deleteRetrievalLog(String id) {
        retrievalLogRepository.deleteById(id);
    }

    /**
     * 获取索引状态
     * 对齐 Go：manager.GetIndexStatus
     */
    public Map<String, Object> getIndexStatus() {
        return knowledgeIndexer.getIndexStatus();
    }

    /**
     * 重建索引
     * 对齐 Go：manager.RebuildIndex
     */
    public void rebuildIndex() {
        knowledgeIndexer.rebuildIndex();
    }

    /**
     * 索引单个知识项
     */
    public void indexItem(String itemId) {
        knowledgeIndexer.indexItem(itemId);
    }

    /**
     * 搜索知识库（混合检索）
     * 对齐 Go：retriever.Search
     */
    public List<KnowledgeItem> search(String query, int topK) {
        KnowledgeRetriever.SearchRequest request = new KnowledgeRetriever.SearchRequest();
        request.setQuery(query);
        request.setTopK(topK);
        
        List<KnowledgeRetriever.RetrievalResult> results = knowledgeRetriever.search(request);
        
        return results.stream()
                .map(KnowledgeRetriever.RetrievalResult::getItem)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 搜索知识库（带风险类型）
     */
    public List<KnowledgeItem> search(String query, String riskType, int topK, double threshold) {
        KnowledgeRetriever.SearchRequest request = new KnowledgeRetriever.SearchRequest();
        request.setQuery(query);
        request.setRiskType(riskType);
        request.setTopK(topK);
        request.setThreshold(threshold);
        
        List<KnowledgeRetriever.RetrievalResult> results = knowledgeRetriever.search(request);
        
        return results.stream()
                .map(KnowledgeRetriever.RetrievalResult::getItem)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取检索结果（包含块信息）
     */
    public List<KnowledgeRetriever.RetrievalResult> searchWithChunks(String query, String riskType, int topK, double threshold) {
        KnowledgeRetriever.SearchRequest request = new KnowledgeRetriever.SearchRequest();
        request.setQuery(query);
        request.setRiskType(riskType);
        request.setTopK(topK);
        request.setThreshold(threshold);
        
        return knowledgeRetriever.search(request);
    }
}
