package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "investigator_automation_job")
public class AutomationJobEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "schedule_kind", nullable = false, length = 20)
    private String scheduleKind;

    @Column(name = "schedule_expr", nullable = false, length = 80)
    private String scheduleExpr;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "payload_kind", nullable = false, length = 20)
    private String payloadKind;

    @Column(name = "session_target", nullable = false, length = 20)
    private String sessionTarget;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "LONGTEXT")
    private String prompt;

    @Column(length = 80)
    private String route;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)")
    private UUID conversationId;

    @Column(name = "delete_after_run", nullable = false)
    private boolean deleteAfterRun;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_status", length = 30)
    private String lastStatus;

    @Column(name = "consecutive_errors", nullable = false)
    private int consecutiveErrors;

    @Column(name = "running_execution_id", columnDefinition = "BINARY(16)")
    private UUID runningExecutionId;

    @Column(name = "running_since")
    private Instant runningSince;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AutomationJobEntity() {
    }

    public AutomationJobEntity(
            UUID id,
            UUID projectId,
            String name,
            String environment,
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
            UUID runningExecutionId,
            Instant runningSince,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.environment = environment;
        this.enabled = enabled;
        this.scheduleKind = scheduleKind;
        this.scheduleExpr = scheduleExpr;
        this.timezone = timezone;
        this.payloadKind = payloadKind;
        this.sessionTarget = sessionTarget;
        this.prompt = prompt;
        this.route = route;
        this.conversationId = conversationId;
        this.deleteAfterRun = deleteAfterRun;
        this.timeoutSeconds = timeoutSeconds;
        this.lastRunAt = lastRunAt;
        this.nextRunAt = nextRunAt;
        this.lastStatus = lastStatus;
        this.consecutiveErrors = consecutiveErrors;
        this.runningExecutionId = runningExecutionId;
        this.runningSince = runningSince;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getScheduleKind() {
        return scheduleKind;
    }

    public String getScheduleExpr() {
        return scheduleExpr;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getPayloadKind() {
        return payloadKind;
    }

    public String getSessionTarget() {
        return sessionTarget;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getRoute() {
        return route;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public boolean isDeleteAfterRun() {
        return deleteAfterRun;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public int getConsecutiveErrors() {
        return consecutiveErrors;
    }

    public UUID getRunningExecutionId() {
        return runningExecutionId;
    }

    public Instant getRunningSince() {
        return runningSince;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
