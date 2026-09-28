package com.maiya.investigator.domain.memory;

import java.time.Instant;
import java.util.UUID;

public record MemoryFact(
        UUID id,
        UUID projectId,
        String kind,
        String content,
        UUID sourceConversationId,
        UUID qdrantPointId,
        Instant createdAt,
        Instant updatedAt
) {
    public static MemoryFact create(UUID projectId, String kind, String content, UUID sourceConversationId) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        return new MemoryFact(id, projectId, kind, content, sourceConversationId, null, now, now);
    }

    public MemoryFact withQdrantPointId(UUID pointId) {
        return new MemoryFact(id, projectId, kind, content, sourceConversationId, pointId, createdAt, Instant.now());
    }
}
