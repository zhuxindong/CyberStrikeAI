package com.cyberstrike.controller;

import com.cyberstrike.repository.ConversationRepository;
import com.cyberstrike.repository.MessageRepository;
import com.cyberstrike.repository.ToolExecutionRepository;
import com.cyberstrike.repository.VulnerabilityRepository;
import com.cyberstrike.entity.Conversation;
import com.cyberstrike.entity.Message;
import com.cyberstrike.entity.ToolExecution;
import com.cyberstrike.entity.Vulnerability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/openapi")
@Tag(name = "OpenAPI", description = "对外结果聚合接口")
@SecurityRequirement(name = "bearerAuth")
public class OpenApiController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final ToolExecutionRepository toolExecutionRepository;

    public OpenApiController(ConversationRepository conversationRepository,
                             MessageRepository messageRepository,
                             VulnerabilityRepository vulnerabilityRepository,
                             ToolExecutionRepository toolExecutionRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.vulnerabilityRepository = vulnerabilityRepository;
        this.toolExecutionRepository = toolExecutionRepository;
    }

    @Operation(
            summary = "获取对话结果聚合",
            description = "聚合返回指定对话的消息、漏洞、工具执行记录",
            responses = {
                    @ApiResponse(responseCode = "200", description = "成功",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "对话不存在",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Map.class)))
            })
    @GetMapping("/conversations/{id}/results")
    public ResponseEntity<?> getConversationResults(@PathVariable String id) {
        Conversation conv = conversationRepository.findById(id).orElse(null);
        if (conv == null) {
            return ResponseEntity.notFound().build();
        }

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(id);
        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findByConversationId(id);
        List<ToolExecution> executions = toolExecutionRepository.findByConversationIdOrderByCreatedAtDesc(id);

        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conv.getId());
        result.put("messages", messages);
        result.put("vulnerabilities", vulnerabilities);
        result.put("executionResults", executions);
        return ResponseEntity.ok(result);
    }
}
