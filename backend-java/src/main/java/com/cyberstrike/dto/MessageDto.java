package com.cyberstrike.dto;

import com.cyberstrike.entity.Message;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MessageDto {
    private String id;

    private String conversationId;

    private String role;

    private String content;

    private String mcpExecutionIds;

    private LocalDateTime createdAt;

    private String functionName;

    private String resultStatus;

    private String type;

    private List<Message> messageList;
}
