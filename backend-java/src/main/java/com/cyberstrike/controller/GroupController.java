package com.cyberstrike.controller;

import com.cyberstrike.entity.Conversation;
import com.cyberstrike.entity.Group;
import com.cyberstrike.entity.GroupConversation;
import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.GroupConversationRepository;
import com.cyberstrike.repository.GroupRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups", description = "对话分组管理接口")
public class GroupController {

    private final GroupRepository groupRepository;
    private final GroupConversationRepository groupConversationRepository;
    private final ConversationRepository conversationRepository;

    public GroupController(GroupRepository groupRepository,
            GroupConversationRepository groupConversationRepository,
            ConversationRepository conversationRepository) {
        this.groupRepository = groupRepository;
        this.groupConversationRepository = groupConversationRepository;
        this.conversationRepository = conversationRepository;
    }

    @Operation(summary = "创建分组", description = "POST /api/groups")
    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody Map<String, String> body) {
        Group group = new Group();
        group.setId(UUID.randomUUID().toString());
        group.setName(body.getOrDefault("name", "新分组"));
        group.setIcon(body.get("icon"));
        group.setPinned(false);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupRepository.save(group);
        return ResponseEntity.ok(group);
    }

    @Operation(summary = "列出所有分组", description = "GET /api/groups")
    @GetMapping
    public ResponseEntity<?> listGroups() {
        List<Group> groups = groupRepository.findAllByOrderByPinnedDescCreatedAtDesc();
        return ResponseEntity.ok(groups);
    }

    @Operation(summary = "获取分组详情", description = "GET /api/groups/{id}")
    @GetMapping("/{id}")
    public ResponseEntity<?> getGroup(@Parameter(description = "分组ID") @PathVariable String id) {
        return groupRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "更新分组", description = "PUT /api/groups/{id}")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateGroup(@Parameter(description = "分组ID") @PathVariable String id, @RequestBody Map<String, String> body) {
        return groupRepository.findById(id)
                .map(group -> {
                    if (body.containsKey("name"))
                        group.setName(body.get("name"));
                    if (body.containsKey("icon"))
                        group.setIcon(body.get("icon"));
                    group.setUpdatedAt(LocalDateTime.now());
                    groupRepository.save(group);
                    return ResponseEntity.ok(group);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "删除分组", description = "DELETE /api/groups/{id}")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGroup(@Parameter(description = "分组ID") @PathVariable String id) {
        if (groupRepository.existsById(id)) {
            groupConversationRepository.deleteByGroupId(id);
            groupRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(summary = "将对话加入分组", description = "POST /api/groups/conversation")
    @PostMapping("/conversation")
    public ResponseEntity<?> addConversationToGroup(@RequestBody Map<String, String> body) {
        String conversationId = body.get("conversationId");
        String groupId = body.get("groupId");

        if (conversationId == null || groupId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少必要参数"));
        }

        if (!groupRepository.existsById(groupId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "分组不存在"));
        }

        if (!conversationRepository.existsById(conversationId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "对话不存在"));
        }

        if (groupConversationRepository.existsByGroupIdAndConversationId(groupId, conversationId)) {
            return ResponseEntity.ok(Map.of("message", "对话已在分组中"));
        }

        GroupConversation gc = new GroupConversation();
        gc.setGroupId(groupId);
        gc.setConversationId(conversationId);
        gc.setPinnedInGroup(false);
        gc.setCreatedAt(LocalDateTime.now());
        groupConversationRepository.save(gc);

        return ResponseEntity.ok(Map.of("message", "添加成功"));
    }

    @Operation(summary = "从分组移除对话", description = "DELETE /api/groups/{gid}/conversations/{cid}")
    @DeleteMapping("/{gid}/conversations/{cid}")
    public ResponseEntity<?> removeConversationFromGroup(@Parameter(description = "分组ID") @PathVariable String gid, @Parameter(description = "对话ID") @PathVariable String cid) {
        groupConversationRepository.deleteByGroupIdAndConversationId(gid, cid);
        return ResponseEntity.ok(Map.of("message", "移除成功"));
    }

    @Operation(summary = "获取分组下对话列表", description = "GET /api/groups/{id}/conversations")
    @GetMapping("/{id}/conversations")
    public ResponseEntity<?> getGroupConversations(@Parameter(description = "分组ID") @PathVariable String id) {
        if (!groupRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<GroupConversation> gcs = groupConversationRepository.findByGroupId(id);
        List<Map<String, Object>> result = new ArrayList<>();

        for (GroupConversation gc : gcs) {
            conversationRepository.findById(gc.getConversationId()).ifPresent(conv -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", conv.getId());
                item.put("title", conv.getTitle());
                item.put("pinned", conv.getPinned());
                item.put("groupPinned", gc.getPinnedInGroup());
                item.put("createdAt", conv.getCreatedAt());
                item.put("updatedAt", conv.getUpdatedAt());
                result.add(item);
            });
        }

        // 排序：groupPinned 优先，然后按 updatedAt 倒序
        result.sort((a, b) -> {
            Boolean pinnedA = (Boolean) a.get("groupPinned");
            Boolean pinnedB = (Boolean) b.get("groupPinned");
            if (!Objects.equals(pinnedA, pinnedB)) {
                return Boolean.compare(pinnedB, pinnedA);
            }
            LocalDateTime timeA = (LocalDateTime) a.get("updatedAt");
            LocalDateTime timeB = (LocalDateTime) b.get("updatedAt");
            return timeB.compareTo(timeA);
        });

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "置顶/取消置顶分组", description = "PUT /api/groups/{id}/pinned")
    @PutMapping("/{id}/pinned")
    public ResponseEntity<?> updateGroupPinned(@Parameter(description = "分组ID") @PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return groupRepository.findById(id)
                .map(group -> {
                    group.setPinned(body.getOrDefault("pinned", false));
                    group.setUpdatedAt(LocalDateTime.now());
                    groupRepository.save(group);
                    return ResponseEntity.ok(group);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "更新对话在分组中的置顶状态", description = "PUT /api/groups/{gid}/conversations/{cid}/pinned")
    @PutMapping("/{gid}/conversations/{cid}/pinned")
    public ResponseEntity<?> updateConversationPinnedInGroup(
            @Parameter(description = "分组ID") @PathVariable String gid,
            @Parameter(description = "对话ID") @PathVariable String cid,
            @RequestBody Map<String, Boolean> body) {

        return groupConversationRepository.findByGroupIdAndConversationId(gid, cid)
                .map(gc -> {
                    gc.setPinnedInGroup(body.getOrDefault("pinned", false));
                    groupConversationRepository.save(gc);
                    return ResponseEntity.ok(Map.of("message", "更新成功"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
