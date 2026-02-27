package com.cyberstrike.tool;

public class ToolContext {
    // 使用 ThreadLocal 来存储当前会话的 conversationId
    private static final ThreadLocal<String> CURRENT_CONVERSATION_ID = new ThreadLocal<>();

    public static void setConversationId(String id) {
        CURRENT_CONVERSATION_ID.set(id);
    }

    public static String getConversationId() {
        return CURRENT_CONVERSATION_ID.get();
    }

    public static void clear() {
        CURRENT_CONVERSATION_ID.remove();
    }
}