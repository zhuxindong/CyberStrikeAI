package com.cyberstrike.tool;

import com.cyberstrike.entity.AgentMetadata;
import com.cyberstrike.service.openai.OpenAiService;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionMessage;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionRequest;
import com.cyberstrike.service.openai.model.OpenAIModels.ChatCompletionResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SubAgent {

    private final AgentMetadata metadata;
    private final OpenAiService openAiService;

    public SubAgent(AgentMetadata metadata, OpenAiService openAiService) {
        this.metadata = metadata;
        this.openAiService = openAiService;
    }

    /**
     * 执行子代理任务
     */
    public String execute(String description, String model) {
        log.info("执行子代理: {}, 任务: {}", metadata.getName(), description);

        try {
            List<ChatCompletionMessage> messages = new ArrayList<>();

            // 系统提示词
            messages.add(ChatCompletionMessage.builder()
                    .role("system")
                    .content(metadata.getInstruction())
                    .build());

            // 用户任务描述
            messages.add(ChatCompletionMessage.builder()
                    .role("user")
                    .content(description)
                    .build());

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model != null ? model : "gpt-4")
                    .messages(messages)
                    .build();

            ChatCompletionResponse response = openAiService.chatCompletion(request);

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String result = response.getChoices().get(0).getMessage().getContent();
                if (result == null || result.isEmpty()) {
                    return "子代理执行完成，但未返回结果。";
                }
                return result;
            }

            return "子代理执行完成，但未收到响应。";

        } catch (Exception e) {
            log.error("子代理执行失败: {}", metadata.getName(), e);
            return "子代理执行失败: " + e.getMessage();
        }
    }

    public String getName() {
        return metadata.getName();
    }

    public String getDescription() {
        return metadata.getDescription();
    }

    public String getAgentId() {
        return metadata.getAgentId();
    }
}