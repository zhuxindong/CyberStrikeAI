package com.cyberstrike.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChatResponse {
    private String response;
    private List<String> mcpExecutionIds;
    private String conversationId;
    private LocalDateTime time;

    public ChatResponse() {
    }

    public ChatResponse(String response, List<String> mcpExecutionIds, String conversationId) {
        this.response = response;
        this.mcpExecutionIds = mcpExecutionIds;
        this.conversationId = conversationId;
        this.time = LocalDateTime.now();
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public List<String> getMcpExecutionIds() {
        return mcpExecutionIds;
    }

    public void setMcpExecutionIds(List<String> mcpExecutionIds) {
        this.mcpExecutionIds = mcpExecutionIds;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }
}
