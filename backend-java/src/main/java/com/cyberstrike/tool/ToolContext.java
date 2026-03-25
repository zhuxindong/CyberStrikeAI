package com.cyberstrike.tool;

public class ToolContext {
    // 使用 ThreadLocal 来存储当前会话的 conversationId
    private static final ThreadLocal<String> CURRENT_CONVERSATION_ID = new ThreadLocal<>();
    // 使用 ThreadLocal 来存储当前消息的 messageId
    private static final ThreadLocal<String> CURRENT_MESSAGE_ID = new ThreadLocal<>();

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

    public static void clear() {
        CURRENT_CONVERSATION_ID.remove();
        CURRENT_MESSAGE_ID.remove();
    }
}