package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigator_model_usage")
public class ModelUsageEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "role_codes", nullable = false, length = 400)
    private String roleCodes;

    @Column(name = "project_id", columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "prompt_tokens")
    private Long promptTokens;

    @Column(name = "completion_tokens")
    private Long completionTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    protected ModelUsageEntity() {
    }

    public ModelUsageEntity(
            UUID id,
            Instant occurredAt,
            UUID userId,
            String username,
            String roleCodes,
            UUID projectId,
            String provider,
            String model,
            int callCount,
            Long promptTokens,
            Long completionTokens,
            Long totalTokens
    ) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.username = username;
        this.roleCodes = roleCodes;
        this.projectId = projectId;
        this.provider = provider;
        this.model = model;
        this.callCount = callCount;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRoleCodes() {
        return roleCodes;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getCallCount() {
        return callCount;
    }

    public Long getPromptTokens() {
        return promptTokens;
    }

    public Long getCompletionTokens() {
        return completionTokens;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }
}
