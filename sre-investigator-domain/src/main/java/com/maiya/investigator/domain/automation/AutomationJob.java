package com.maiya.investigator.domain.automation;

import com.maiya.investigator.api.Environment;
import java.time.Instant;
import java.util.UUID;

public record AutomationJob(
        UUID id,
        UUID projectId,
        String name,
        Environment environment,
        boolean enabled,
        ScheduleKind scheduleKind,
        String scheduleExpr,
        String timezone,
        PayloadKind payloadKind,
        SessionTarget sessionTarget,
        String prompt,
        String route,
        UUID conversationId,
        boolean deleteAfterRun,
        int timeoutSeconds,
        Instant lastRunAt,
        Instant nextRunAt,
        String lastStatus,
        int consecutiveErrors,
        UUID runningExecutionId,
        Instant runningSince,
        Instant createdAt,
        Instant updatedAt
) {
    public static AutomationJob create(
            UUID projectId,
            String name,
            Environment environment,
            ScheduleKind scheduleKind,
            String scheduleExpr,
            String timezone,
            PayloadKind payloadKind,
            SessionTarget sessionTarget,
            String prompt,
            String route,
            UUID conversationId,
            boolean deleteAfterRun,
            int timeoutSeconds,
            Instant nextRunAt
    ) {
        Instant now = Instant.now();
        return new AutomationJob(
                UUID.randomUUID(),
                projectId,
                name,
                environment,
                true,
                scheduleKind,
                scheduleExpr,
                timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone,
                payloadKind,
                sessionTarget == null ? SessionTarget.ISOLATED : sessionTarget,
                prompt,
                route,
                conversationId,
                deleteAfterRun,
                timeoutSeconds <= 0 ? 600 : timeoutSeconds,
                null,
                nextRunAt,
                null,
                0,
                null,
                null,
                now,
                now
        );
    }

    public AutomationJob withEnabled(boolean enabled) {
        return copy(enabled, lastRunAt, nextRunAt, lastStatus, consecutiveErrors, runningExecutionId, runningSince);
    }

    public AutomationJob markingRun(UUID executionId, Instant now) {
        return new AutomationJob(
                id, projectId, name, environment, enabled, scheduleKind, scheduleExpr, timezone,
                payloadKind, sessionTarget, prompt, route, conversationId, deleteAfterRun, timeoutSeconds,
                lastRunAt, nextRunAt, lastStatus, consecutiveErrors, executionId, now, createdAt, now
        );
    }

    public AutomationJob finished(Instant now, Instant next, String status, int errors, boolean disable) {
        return new AutomationJob(
                id, projectId, name, environment, enabled && !disable, scheduleKind, scheduleExpr, timezone,
                payloadKind, sessionTarget, prompt, route, conversationId, deleteAfterRun, timeoutSeconds,
                now, next, status, errors, null, null, createdAt, now
        );
    }

    public AutomationJob withConversationId(UUID conversationId) {
        Instant now = Instant.now();
        return new AutomationJob(
                id, projectId, name, environment, enabled, scheduleKind, scheduleExpr, timezone,
                payloadKind, sessionTarget, prompt, route, conversationId, deleteAfterRun, timeoutSeconds,
                lastRunAt, nextRunAt, lastStatus, consecutiveErrors, runningExecutionId, runningSince,
                createdAt, now
        );
    }

    public AutomationJob updated(
            String name,
            Environment environment,
            boolean enabled,
            ScheduleKind scheduleKind,
            String scheduleExpr,
            String timezone,
            PayloadKind payloadKind,
            SessionTarget sessionTarget,
            String prompt,
            String route,
            UUID conversationId,
            boolean deleteAfterRun,
            int timeoutSeconds,
            Instant nextRunAt
    ) {
        Instant now = Instant.now();
        return new AutomationJob(
                id,
                projectId,
                name,
                environment,
                enabled,
                scheduleKind,
                scheduleExpr,
                timezone == null || timezone.isBlank() ? this.timezone : timezone,
                payloadKind,
                sessionTarget == null ? this.sessionTarget : sessionTarget,
                prompt,
                route,
                conversationId,
                deleteAfterRun,
                timeoutSeconds <= 0 ? this.timeoutSeconds : timeoutSeconds,
                lastRunAt,
                nextRunAt,
                lastStatus,
                consecutiveErrors,
                runningExecutionId,
                runningSince,
                createdAt,
                now
        );
    }

    public boolean isRunning(Instant now) {
        if (runningExecutionId == null || runningSince == null) {
            return false;
        }
        return runningSince.plusSeconds(Math.max(60, timeoutSeconds)).isAfter(now);
    }

    private AutomationJob copy(
            boolean enabled,
            Instant lastRunAt,
            Instant nextRunAt,
            String lastStatus,
            int consecutiveErrors,
            UUID runningExecutionId,
            Instant runningSince
    ) {
        Instant now = Instant.now();
        return new AutomationJob(
                id, projectId, name, environment, enabled, scheduleKind, scheduleExpr, timezone,
                payloadKind, sessionTarget, prompt, route, conversationId, deleteAfterRun, timeoutSeconds,
                lastRunAt, nextRunAt, lastStatus, consecutiveErrors, runningExecutionId, runningSince,
                createdAt, now
        );
    }
}
