package com.maiya.investigator.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AutomationExecutionDetailResponse(
        AutomationJobResponse job,
        AutomationExecutionResponse execution,
        UUID conversationId,
        List<MessageView> messages
) {
    public record MessageView(
            UUID id,
            String role,
            String content,
            UUID runId,
            Instant createdAt
    ) {
    }
}
