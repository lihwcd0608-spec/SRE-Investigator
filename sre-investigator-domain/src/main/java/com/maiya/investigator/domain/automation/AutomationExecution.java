package com.maiya.investigator.domain.automation;

import java.time.Instant;
import java.util.UUID;

public record AutomationExecution(
        UUID id,
        UUID jobId,
        UUID runId,
        UUID conversationId,
        TriggerType triggerType,
        String status,
        String summary,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
    public static AutomationExecution start(UUID jobId, TriggerType triggerType) {
        Instant now = Instant.now();
        return new AutomationExecution(
                UUID.randomUUID(),
                jobId,
                null,
                null,
                triggerType,
                "RUNNING",
                null,
                null,
                now,
                null
        );
    }

    public AutomationExecution complete(UUID runId, UUID conversationId, String status, String summary, String error) {
        return new AutomationExecution(
                id,
                jobId,
                runId,
                conversationId,
                triggerType,
                status,
                summary,
                error,
                startedAt,
                Instant.now()
        );
    }
}
