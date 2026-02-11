package com.cyberstrike.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @UuidGenerator
    private String id;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String role;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(name = "mcp_execution_ids", columnDefinition = "TEXT")
    private String mcpExecutionIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "function_name", nullable = false)
    private String functionName;

    @Column(name = "result_status", nullable = false)
    private String resultStatus;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "iteration", nullable = false)
    private String iteration;

    @Column(name = "data_json", columnDefinition = "LONGTEXT", nullable = false)
    private String dataJson;

    @Column(name = "toolId", nullable = false)
    private String toolId;

    @Column(name = "time", nullable = false)
    private Integer time;

    @Column(name = "del_flag", nullable = false)
    private Integer delFlag=0;

    public Message() {
    }

    public Message(String conversationId, String role, String content, String functionName, String resultStatus, String type, String requestId, String iteration, String dataJson, String toolId, Integer time, Integer delFlag) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.functionName = functionName;
        this.resultStatus = resultStatus;
        this.type = type;
        this.requestId = requestId;
        this.iteration = iteration;
        this.dataJson = dataJson;
        this.toolId = toolId;
        this.time = time;
    }

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.createdAt == null)
            this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMcpExecutionIds() {
        return mcpExecutionIds;
    }

    public void setMcpExecutionIds(String mcpExecutionIds) {
        this.mcpExecutionIds = mcpExecutionIds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIteration() {
        return iteration;
    }

    public void setIteration(String iteration) {
        this.iteration = iteration;
    }

    public String getDataJson() {
        return dataJson;
    }

    public void setDataJson(String dataJson) {
        this.dataJson = dataJson;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
