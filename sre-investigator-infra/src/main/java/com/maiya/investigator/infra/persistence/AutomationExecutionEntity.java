package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigator_automation_execution")
public class AutomationExecutionEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "job_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID jobId;

    @Column(name = "run_id", columnDefinition = "BINARY(16)")
    private UUID runId;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)")
    private UUID conversationId;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 400)
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AutomationExecutionEntity() {
    }

    public AutomationExecutionEntity(
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
        this.id = id;
        this.jobId = jobId;
        this.runId = runId;
        this.conversationId = conversationId;
        this.triggerType = triggerType;
        this.status = status;
        this.summary = summary;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
