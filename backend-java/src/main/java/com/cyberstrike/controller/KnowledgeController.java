package com.cyberstrike.controller;

import com.cyberstrike.entity.KnowledgeItem;
import com.cyberstrike.repository.KnowledgeItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeItemRepository knowledgeItemRepository;

    public KnowledgeController(KnowledgeItemRepository knowledgeItemRepository) {
        this.knowledgeItemRepository = knowledgeItemRepository;
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        List<String> categories = knowledgeItemRepository.findDistinctCategories();
        return ResponseEntity.ok(Map.of("categories", categories));
    }

    @GetMapping("/items")
    public ResponseEntity<?> getItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        List<KnowledgeItem> items;
        if (search != null && !search.isBlank()) {
            items = knowledgeItemRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(search,
                    search);
        } else if (category != null && !category.isBlank()) {
            items = knowledgeItemRepository.findByCategoryOrderByCreatedAtDesc(category);
        } else {
            items = knowledgeItemRepository.findAll();
        }

        int offset = (page - 1) * limit;
        int end = Math.min(offset + limit, items.size());
        List<KnowledgeItem> pageItems = (offset < items.size()) ? items.subList(offset, end) : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("items", pageItems);
        result.put("total", items.size());
        result.put("page", page);
        result.put("limit", limit);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<?> getItem(@PathVariable String id) {
        return knowledgeItemRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/items")
    public ResponseEntity<?> createItem(@RequestBody KnowledgeItem body) {
        body.setId(UUID.randomUUID().toString());
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        knowledgeItemRepository.save(body);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<?> updateItem(@PathVariable String id, @RequestBody KnowledgeItem body) {
        return knowledgeItemRepository.findById(id)
                .map(item -> {
                    if (body.getCategory() != null)
                        item.setCategory(body.getCategory());
                    if (body.getTitle() != null)
                        item.setTitle(body.getTitle());
                    if (body.getContent() != null)
                        item.setContent(body.getContent());
                    item.setUpdatedAt(LocalDateTime.now());
                    // 更新内容后需要重新生成 embedding (这里暂不实现)
                    item.setEmbedding(null);
                    knowledgeItemRepository.save(item);
                    return ResponseEntity.ok(item);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable String id) {
        if (knowledgeItemRepository.existsById(id)) {
            knowledgeItemRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/index-status")
    public ResponseEntity<?> getIndexStatus() {
        long total = knowledgeItemRepository.count();
        long indexed = knowledgeItemRepository.countIndexed();
        return ResponseEntity.ok(Map.of(
                "total_items", total,
                "indexed_items", indexed));
    }

    @PostMapping("/index")
    public ResponseEntity<?> rebuildIndex() {
        // TODO: 实现向量索引重建逻辑
        // 这需要调用 Embedding API 为每个 item 生成向量
        return ResponseEntity.ok(Map.of("message", "索引重建任务已启动"));
    }

    @GetMapping("/retrieval-logs")
    public ResponseEntity<?> getRetrievalLogs(@RequestParam(required = false) String conversationId) {
        // TODO: 实现检索日志查询
        return ResponseEntity.ok(List.of());
    }
}
