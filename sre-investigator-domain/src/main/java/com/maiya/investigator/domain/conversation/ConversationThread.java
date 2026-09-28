package com.maiya.investigator.domain.conversation;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import java.time.Instant;
import java.util.UUID;

public record ConversationThread(
        UUID id,
        UUID projectId,
        Environment environment,
        ConversationMode mode,
        ConversationOrigin origin,
        String title,
        String memorySummary,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConversationThread start(UUID projectId, Environment environment, ConversationMode mode, String title) {
        return start(projectId, environment, mode, ConversationOrigin.CONVERSATION, title, null);
    }

    public static ConversationThread start(
            UUID projectId,
            Environment environment,
            ConversationMode mode,
            ConversationOrigin origin,
            String title
    ) {
        return start(projectId, environment, mode, origin, title, null);
    }

    public static ConversationThread start(
            UUID projectId,
            Environment environment,
            ConversationMode mode,
            ConversationOrigin origin,
            String title,
            UUID createdByUserId
    ) {
        Instant now = Instant.now();
        return new ConversationThread(
                UUID.randomUUID(),
                projectId,
                environment,
                mode == null ? ConversationMode.INVESTIGATE : mode,
                origin == null ? ConversationOrigin.CONVERSATION : origin,
                title,
                null,
                createdByUserId,
                now,
                now
        );
    }

    public ConversationThread touched() {
        return touched(title);
    }

    public ConversationThread touched(String title) {
        return new ConversationThread(
                id,
                projectId,
                environment,
                mode,
                origin,
                title == null ? this.title : title,
                memorySummary,
                createdByUserId,
                createdAt,
                Instant.now()
        );
    }

    public ConversationThread withMemorySummary(String summary) {
        return new ConversationThread(
                id,
                projectId,
                environment,
                mode,
                origin,
                title,
                summary,
                createdByUserId,
                createdAt,
                Instant.now()
        );
    }
}
