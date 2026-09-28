package com.maiya.investigator.graph.conversation;

/**
 * 会话记忆入口。回放已落库的滚动摘要，由 ConversationMemoryCompactor 写入。
 */
public final class ConversationMemory {
    private ConversationMemory() {
    }

    public static String current(String memorySummary) {
        if (memorySummary == null || memorySummary.isBlank()) {
            return null;
        }
        return memorySummary.trim();
    }
}
