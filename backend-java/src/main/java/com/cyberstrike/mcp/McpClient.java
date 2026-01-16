package com.cyberstrike.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 客户端接口
 */
public interface McpClient {

    /**
     * 初始化连接
     */
    void initialize() throws McpException;

    /**
     * 列出所有可用工具
     */
    List<McpTypes.Tool> listTools() throws McpException;

    /**
     * 调用工具
     */
    McpTypes.ToolResult callTool(String name, Map<String, Object> arguments) throws McpException;

    /**
     * 关闭连接
     */
    void close();

    /**
     * 检查是否已连接
     */
    boolean isConnected();

    /**
     * 获取当前状态
     */
    String getStatus();

    /**
     * 获取已获取的工具数量
     */
    int getToolCount();
}
