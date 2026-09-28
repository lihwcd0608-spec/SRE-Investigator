package com.maiya.investigator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AutomationJobRequest(
        @NotBlank String name,
        @NotNull Environment environment,
        Boolean enabled,
        @NotBlank String scheduleKind,
        @NotBlank String scheduleExpr,
        String timezone,
        @NotBlank String payloadKind,
        String sessionTarget,
        String prompt,
        String route,
        UUID conversationId,
        Boolean deleteAfterRun,
        Integer timeoutSeconds
) {
}
