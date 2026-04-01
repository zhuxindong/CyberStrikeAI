package com.cyberstrike.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String conversationId;
    private String role; // Role name for persona
    private String webshellConnectionId;  // 新增字段
}
