package com.maiya.investigator.domain.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessage(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        UUID runId,
        Instant createdAt
) {
    public static ConversationMessage user(UUID conversationId, String content, UUID runId) {
        return new ConversationMessage(UUID.randomUUID(), conversationId, "user", content, runId, Instant.now());
    }

    public static ConversationMessage assistant(UUID conversationId, String content, UUID runId) {
        return new ConversationMessage(UUID.randomUUID(), conversationId, "assistant", content, runId, Instant.now());
    }
}
