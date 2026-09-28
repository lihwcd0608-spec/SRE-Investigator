package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.domain.conversation.ConversationMessage;
import java.util.List;

public final class TurnHistoryPrompt {
    static final int MAX_MESSAGE_CHARS = 800;

    private TurnHistoryPrompt() {
    }

    public static String append(String question, String memorySummary, List<ConversationMessage> prior) {
        return append(question, memorySummary, prior, List.of());
    }

    public static String append(
            String question,
            String memorySummary,
            List<ConversationMessage> prior,
            List<String> projectFacts
    ) {
        String blocks = blocks(memorySummary, prior, projectFacts);
        if (blocks.isEmpty()) {
            return question == null ? "" : question;
        }
        return blocks + "## 当前问题\n" + (question == null ? "" : question);
    }

    public static String blocks(String memorySummary, List<ConversationMessage> prior, List<String> projectFacts) {
        boolean hasMemory = ConversationMemory.current(memorySummary) != null;
        boolean hasHistory = prior != null && !prior.isEmpty();
        boolean hasFacts = projectFacts != null && !projectFacts.isEmpty();
        if (!hasMemory && !hasHistory && !hasFacts) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        text.append("这是同一会话的后续提问。请结合此前上下文回答，不要假装这是全新的一次对话。\n");
        if (hasFacts) {
            text.append("\n## 项目记忆\n");
            for (String fact : projectFacts) {
                if (fact != null && !fact.isBlank()) {
                    text.append("- ").append(fact.trim()).append('\n');
                }
            }
        }
        String memory = ConversationMemory.current(memorySummary);
        if (memory != null) {
            text.append("\n## 会话记忆\n").append(memory).append('\n');
        }
        if (hasHistory) {
            text.append("\n## 最近对话\n");
            for (ConversationMessage message : prior) {
                String role = "user".equals(message.role()) ? "用户" : "助手";
                text.append(role).append("：\n").append(clip(message.content())).append("\n\n");
            }
        }
        return text.toString();
    }

    static String clip(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_MESSAGE_CHARS) {
            return content;
        }
        return content.substring(0, MAX_MESSAGE_CHARS) + "\n…(已截断)";
    }
}
