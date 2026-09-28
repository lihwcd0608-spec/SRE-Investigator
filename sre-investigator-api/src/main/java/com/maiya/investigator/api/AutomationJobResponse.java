package com.maiya.investigator.api;

import java.time.Instant;
import java.util.UUID;

public record AutomationJobResponse(
        UUID id,
        UUID projectId,
        String name,
        Environment environment,
        boolean enabled,
        String scheduleKind,
        String scheduleExpr,
        String timezone,
        String payloadKind,
        String sessionTarget,
        String prompt,
        String route,
        UUID conversationId,
        boolean deleteAfterRun,
        int timeoutSeconds,
        Instant lastRunAt,
        Instant nextRunAt,
        String lastStatus,
        int consecutiveErrors,
        boolean running,
        Instant createdAt,
        Instant updatedAt
) {
}
