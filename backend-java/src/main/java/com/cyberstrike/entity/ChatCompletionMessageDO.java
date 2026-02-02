package com.cyberstrike.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime; // 对应 DATETIME
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "chat_completion_message")
public class ChatCompletionMessageDO {

    // 如果你没有在数据库中显式定义主键ID，JPA可能会报错。
    // 如果数据库表里没有ID主键，请务必删除以下三行：
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "conversation_id", length = 255)
    private String conversationId;

    @Column(name = "role", length = 255)
    private String role;

    @Lob
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "tool_call_id", length = 255)
    private String toolCallId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "tool_calls")
    private String toolCalls;

    /**
     * 对应数据库字段: create_time (类型 DATETIME)
     * 使用 LocalDateTime 精确映射 "年-月-日 时:分:秒" 格式
     */
    @Column(name = "create_time")
    private Date createTime;
}