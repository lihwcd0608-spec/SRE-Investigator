package com.maiya.investigator.domain.conversation;

public enum ConversationOrigin {
    CONVERSATION,
    AUTOMATION,
    REVIEW,
    AGENT;

    public static ConversationOrigin from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CONVERSATION;
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return CONVERSATION;
        }
    }
}
