package com.maiya.investigator.domain;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import java.time.Instant;
import java.util.UUID;

public record InvestigationRun(
    UUID id,
    UUID projectId,
    Environment environment,
    ConversationMode mode,
    String question,
    RunStatus status,
    String report,
    Instant createdAt,
    Instant updatedAt
) {
    public static InvestigationRun start(
            UUID projectId,
            Environment environment,
            ConversationMode mode,
            String question
    ) {
        Instant now = Instant.now();
        return new InvestigationRun(
                UUID.randomUUID(),
                projectId,
                environment,
                mode == null ? ConversationMode.INVESTIGATE : mode,
                question,
                RunStatus.RUNNING,
                null,
                now,
                now
        );
    }

    public InvestigationRun complete(String report, RunStatus status) {
        return new InvestigationRun(id, projectId, environment, mode, question, status, report, createdAt, Instant.now());
    }

    public enum RunStatus {
        RUNNING, NEEDS_INPUT, COMPLETED, FAILED
    }
}
