package com.cyberstrike.controller;

import com.cyberstrike.entity.Conversation;
import com.cyberstrike.entity.Message;
import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.MessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationController(ConversationRepository conversationRepository,
            MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    // 创建对话
    @PostMapping
    public ResponseEntity<Conversation> createConversation(@RequestBody Map<String, String> body) {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setTitle(body.getOrDefault("title", "新对话"));
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setPinned(false);
        conversationRepository.save(conversation);
        return ResponseEntity.ok(conversation);
    }

    // 对话列表
    @GetMapping
    public ResponseEntity<List<Conversation>> listConversations(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String search) {
        List<Conversation> conversations;
        if (search != null && !search.isBlank()) {
            conversations = conversationRepository.findByTitleContainingOrderByUpdatedAtDesc(search);
        } else {
            conversations = conversationRepository.findAllByOrderByUpdatedAtDesc();
        }
        // Manual pagination
        int end = Math.min(offset + limit, conversations.size());
        if (offset >= conversations.size()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(conversations.subList(offset, end));
    }

    // 获取对话详情 (包含消息)
    @GetMapping("/{id}")
    public ResponseEntity<?> getConversation(@PathVariable String id) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(id);
                    Map<String, Object> result = new HashMap<>();
                    result.put("conversation", conversation);
                    result.put("messages", messages);
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 更新对话
    @PutMapping("/{id}")
    public ResponseEntity<?> updateConversation(@PathVariable String id, @RequestBody Map<String, String> body) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    if (body.containsKey("title")) {
                        conversation.setTitle(body.get("title"));
                    }
                    conversation.setUpdatedAt(LocalDateTime.now());
                    conversationRepository.save(conversation);
                    return ResponseEntity.ok(conversation);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 删除对话
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable String id) {
        if (conversationRepository.existsById(id)) {
            // 先删除关联消息
            messageRepository.deleteByConversationId(id);
            conversationRepository.deleteById(id);
            Map<String, String> result = new HashMap<>();
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.notFound().build();
    }

    // 置顶/取消置顶对话
    @PutMapping("/{id}/pinned")
    public ResponseEntity<?> togglePinned(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    conversation.setPinned(body.getOrDefault("pinned", false));
                    conversation.setUpdatedAt(LocalDateTime.now());
                    conversationRepository.save(conversation);
                    return ResponseEntity.ok(conversation);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
