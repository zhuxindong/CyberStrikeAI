package com.cyberstrike.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部 MCP 服务器配置
 */
@Entity
@Table(name = "mcp_servers")
@Data
public class McpServer {

    @Id
    private String id;

    private String name;

    @Column(nullable = false)
    private String transport; // http, sse, stdio

    // For HTTP/SSE transport
    private String url;

    // For stdio transport
    private String command;

    @Column(columnDefinition = "TEXT")
    private String args; // JSON array of arguments

    @Column(columnDefinition = "TEXT")
    private String env; // JSON object of environment variables

    private String status; // connected, disconnected, error

    @Column(name = "tool_count")
    private Integer toolCount = 0;

    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "description")
    private String description;

    @Column(name = "timeout")
    private Integer timeout;

    @Column(name = "tool_enabled")
    private String toolEnabled;

    @Column(name = "enabled")
    private String enabled;

    @Column(name = "error")
    private String error;

}
