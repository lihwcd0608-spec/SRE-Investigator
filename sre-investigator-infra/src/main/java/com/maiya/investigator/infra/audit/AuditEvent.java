package com.maiya.investigator.infra.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        Instant occurredAt,
        String category,
        String action,
        String target,
        UUID actorUserId,
        String actorUsername,
        String clientIp,
        Boolean success,
        Map<String, Object> metadata
) {
    public AuditEvent(UUID id, Instant occurredAt, String action, String target, Map<String, Object> metadata) {
        this(id, occurredAt, "OP", action, target, null, null, null, null, metadata);
    }
}
