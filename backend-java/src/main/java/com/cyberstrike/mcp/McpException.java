package com.cyberstrike.mcp;

/**
 * MCP 异常
 */
public class McpException extends Exception {

    private int code;

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }

    public McpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
