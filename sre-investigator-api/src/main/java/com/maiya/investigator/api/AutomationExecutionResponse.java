package com.maiya.investigator.api;

import java.time.Instant;
import java.util.UUID;

public record AutomationExecutionResponse(
        UUID id,
        UUID jobId,
        UUID runId,
        UUID conversationId,
        String triggerType,
        String status,
        String summary,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
}
