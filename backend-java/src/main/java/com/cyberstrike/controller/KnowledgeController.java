package com.cyberstrike.controller;

import com.cyberstrike.entity.KnowledgeItem;
import com.cyberstrike.repository.KnowledgeItemRepository;
import com.cyberstrike.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeItemRepository repository;
    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeItemRepository repository, KnowledgeService knowledgeService) {
        this.repository = repository;
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public List<KnowledgeItem> getAllItems() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    @PostMapping
    public KnowledgeItem createItem(@RequestBody KnowledgeItem item) {
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return repository.save(item);
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeItem> updateItem(@PathVariable String id, @RequestBody KnowledgeItem itemDetails) {
        KnowledgeItem item = repository.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        item.setTitle(itemDetails.getTitle());
        item.setCategory(itemDetails.getCategory());
        item.setContent(itemDetails.getContent());
        item.setUpdatedAt(LocalDateTime.now());
        // embedding will be rebuilt on next index rebuild or we can trigger it here ?
        // For now, let's nullify it to indicate it needs update (or implementing
        // auto-update later)
        item.setEmbedding(null);

        return ResponseEntity.ok(repository.save(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable String id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return repository.findDistinctCategories();
    }

    /**
     * Trigger explicit index rebuild
     */
    @PostMapping("/index")
    public ResponseEntity<String> rebuildIndex() {
        new Thread(() -> {
            knowledgeService.rebuildIndex();
        }).start();
        return ResponseEntity.ok("Index rebuild triggered in background");
    }

    /**
     * Search test endpoint
     */
    @GetMapping("/search")
    public List<KnowledgeItem> search(@RequestParam String query) {
        return knowledgeService.search(query, 5);
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> getRetrievalLogs() {
        // Placeholder for logs
        return List.of();
    }
}
