package com.cyberstrike.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 【Go -> Java 迁移新增】WebShell AI 对话列表项（用于侧边栏）
 * 对齐 Go 结构体：cyberstrike-ai/internal/database/conversation.go WebShellConversationItem
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebshellConversationItem {

    /**
     * 对话 ID
     */
    private String id;

    /**
     * 对话标题
     */
    private String title;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
}
