package com.cyberstrike.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    private String message;
    private String conversationId;
    private String role; // Role name for persona
    private String webshellConnectionId;  // 新增字段
    private List<Attachment> attachments; // 对话附件（文件上传）
    private HITLRequest hitl; // 人机协同配置

    @Data
    public static class Attachment {
        private String fileName;
        private String mimeType;
        private String serverPath;   // 新增：服务器上的相对路径
    }
}
