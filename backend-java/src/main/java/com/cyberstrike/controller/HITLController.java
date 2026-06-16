package com.cyberstrike.controller;

import com.cyberstrike.dto.HITLRequest;
import com.cyberstrike.entity.HITLInterrupt;
import com.cyberstrike.service.HITLService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hitl")
@CrossOrigin(origins = "*")
@Tag(name = "HITL", description = "人机协同 (Human-in-the-Loop) 审批接口")
public class HITLController {

    private final HITLService hitlService;

    public HITLController(HITLService hitlService) {
        this.hitlService = hitlService;
    }

    @Operation(summary = "获取会话 HITL 配置", description = "GET /api/hitl/config/{conversationId}")
    @GetMapping("/config/{conversationId}")
    public ResponseEntity<?> getHITLConfig(@PathVariable String conversationId) {
        HITLRequest config = hitlService.getConversationConfig(conversationId);
        return ResponseEntity.ok(Map.of(
                "conversationId", conversationId,
                "hitl", config,
                "hitlGlobalToolWhitelist", hitlService.getGlobalToolWhitelist()
        ));
    }

    @Operation(summary = "保存/更新会话 HITL 配置", description = "PUT /api/hitl/config")
    @PutMapping("/config")
    public ResponseEntity<?> upsertHITLConfig(@RequestBody HITLConfigRequest request) {
        hitlService.saveConversationConfig(request.getConversationId(), request.getHitl());
        hitlService.activateConversation(request.getConversationId(), request.getHitl());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "列出待审批中断", description = "GET /api/hitl/pending")
    @GetMapping("/pending")
    public ResponseEntity<?> listPending(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.max(1, Math.min(pageSize, 200));
        List<HITLInterrupt> items = hitlService.listPendingInterrupts(conversationId, page, pageSize);
        return ResponseEntity.ok(Map.of(
                "items", items,
                "page", page,
                "pageSize", pageSize
        ));
    }

    @Operation(summary = "审批决策", description = "POST /api/hitl/decision")
    @PostMapping("/decision")
    public ResponseEntity<?> decide(@RequestBody HITLDecisionRequest request) {
        hitlService.resolveInterrupt(
                request.getInterruptId(),
                request.getDecision(),
                request.getComment(),
                request.getEditedArguments()
        );
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "撤销/忽略审批", description = "POST /api/hitl/dismiss")
    @PostMapping("/dismiss")
    public ResponseEntity<?> dismiss(@RequestBody Map<String, String> body) {
        String interruptId = body.get("interruptId");
        if (interruptId == null || interruptId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "interruptId is required"));
        }
        hitlService.dismissInterrupt(interruptId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "合并全局工具白名单", description = "POST /api/hitl/whitelist/merge")
    @PostMapping("/whitelist/merge")
    public ResponseEntity<?> mergeGlobalWhitelist(@RequestBody MergeWhitelistRequest request) {
        // 简化实现：直接更新内存中的全局白名单
        // 实际项目中可能需要持久化到数据库或配置文件
        hitlService.setGlobalToolWhitelist(request.getSensitiveTools());
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "hitlGlobalToolWhitelist", hitlService.getGlobalToolWhitelist(),
                "hitlGlobalWhitelistMerged", true
        ));
    }

    // ==================== DTO ====================

    public static class HITLConfigRequest {
        private String conversationId;
        private HITLRequest hitl;

        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }
        public HITLRequest getHitl() { return hitl; }
        public void setHitl(HITLRequest hitl) { this.hitl = hitl; }
    }

    public static class HITLDecisionRequest {
        private String interruptId;
        private String decision;
        private String comment;
        private Map<String, Object> editedArguments;

        public String getInterruptId() { return interruptId; }
        public void setInterruptId(String interruptId) { this.interruptId = interruptId; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public Map<String, Object> getEditedArguments() { return editedArguments; }
        public void setEditedArguments(Map<String, Object> editedArguments) { this.editedArguments = editedArguments; }
    }

    public static class MergeWhitelistRequest {
        private List<String> sensitiveTools;

        public List<String> getSensitiveTools() { return sensitiveTools; }
        public void setSensitiveTools(List<String> sensitiveTools) { this.sensitiveTools = sensitiveTools; }
    }
}
