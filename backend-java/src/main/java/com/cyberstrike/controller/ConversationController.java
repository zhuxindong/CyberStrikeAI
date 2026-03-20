package com.cyberstrike.controller;

import com.cyberstrike.dto.MessageDto;
import com.cyberstrike.entity.Conversation;
import com.cyberstrike.entity.Message;
import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.MessageRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
        conversation.setStatus("pending");
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

    @GetMapping("/tasks")
    public ResponseEntity<List<Conversation>> tasks() {
        return ResponseEntity.ok(conversationRepository.findByStatus("running"));
    }

    // 获取对话详情 (包含消息)
    @GetMapping("/{id}")
    public ResponseEntity<?> getConversation(@PathVariable String id) {
        return conversationRepository.findById(id)
                .map(conversation -> {
                    // 1. 查询并排序
                    List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(id);

                    // 2. 建立 ID -> DTO 映射 (DTO里包含初始化的List)
                    Map<String, MessageDto> dtoMap = messages.stream()
                            .collect(Collectors.toMap(
                                    Message::getId,
                                    msg -> {
                                        MessageDto dto = new MessageDto();
                                        BeanUtils.copyProperties(msg, dto);
                                        dto.setMessageList(new ArrayList<>()); // 强制初始化
                                        return dto;
                                    }
                            ));

                    // 3. 核心修复：建立 用户ID -> 总结DTO 的映射
                    // 因为分析和总结都关联到同一个用户ID，我们需要通过用户ID把它们联系起来
                    Map<String, MessageDto> userToSummaryMap = new HashMap<>();
                    for (Message msg : messages) {
                        if ("result".equals(msg.getType())||"cancelled".equals(msg.getType())) {
                            // 总结消息：记录 "它的requestId（用户ID）" 对应的 DTO 是谁
                            userToSummaryMap.put(msg.getRequestId(), dtoMap.get(msg.getId()));
                        }
                    }

                    // 4. 填充数据：遍历所有消息
                    List<MessageDto> result = new ArrayList<>();
                    for (Message msg : messages) {
                        MessageDto dto = dtoMap.get(msg.getId());

                        if ("result".equals(msg.getType())||"cancelled".equals(msg.getType())) {
                            // 总结：加入结果集
                            result.add(dto);
                        } else if ("user".equals(msg.getType())) {
                            // 用户：清空列表，加入结果集
                            dto.setMessageList(null);
                            result.add(dto);
                        } else {
                            // 分析：关键修复
                            // 1. 获取该分析消息关联的 用户ID
                            String userId = msg.getRequestId();
                            // 2. 通过 userToSummaryMap 找到该用户对应的 总结DTO
                            MessageDto summaryDto = userToSummaryMap.get(userId);
                            if (summaryDto != null) {
                                // 3. 把当前分析消息（实体）加入总结DTO的列表
                                summaryDto.getMessageList().add(msg);
                            }
                            // 注意：分析消息不加入主列表
                        }
                    }

                    return ResponseEntity.ok(Map.of("conversation", conversation, "messages", result));
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
