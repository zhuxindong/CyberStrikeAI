package com.cyberstrike.tool;

public class ToolContext {
    // 使用 ThreadLocal 来存储当前会话的 conversationId
    private static final ThreadLocal<String> CURRENT_CONVERSATION_ID = new ThreadLocal<>();
    // 使用 ThreadLocal 来存储当前消息的 messageId
    private static final ThreadLocal<String> CURRENT_MESSAGE_ID = new ThreadLocal<>();
    // 使用 ThreadLocal 来存储当前 WebShell 连接 ID
    private static final ThreadLocal<String> CURRENT_WEBSHELL_CONNECTION_ID = new ThreadLocal<>();

    public static void setConversationId(String id) {
        CURRENT_CONVERSATION_ID.set(id);
    }

    public static String getConversationId() {
        return CURRENT_CONVERSATION_ID.get();
    }

    public static void setMessageId(String id) {
        CURRENT_MESSAGE_ID.set(id);
    }

    public static String getMessageId() {
        return CURRENT_MESSAGE_ID.get();
    }

    // 新增：设置 WebShell 连接 ID
    public static void setWebShellConnectionId(String id) {
        CURRENT_WEBSHELL_CONNECTION_ID.set(id);
    }

    // 新增：获取 WebShell 连接 ID
    public static String getWebShellConnectionId() {
        return CURRENT_WEBSHELL_CONNECTION_ID.get();
    }

    public static void clear() {
        CURRENT_CONVERSATION_ID.remove();
        CURRENT_MESSAGE_ID.remove();
        CURRENT_WEBSHELL_CONNECTION_ID.remove();  // 新增：清理 WebShell 连接 ID
    }
}