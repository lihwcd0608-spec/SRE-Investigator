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
@Table(name = "investigator_tool_binding")
public class ToolBindingEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false)
    private String environment;

    @Column(name = "tool_type", nullable = false)
    private String toolType;

    @Column(nullable = false)
    private String profile;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "encrypted_configuration", columnDefinition = "LONGTEXT")
    private String encryptedConfiguration;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 280)
    private String summary;

    @Column(length = 400)
    private String tags;

    @Column(name = "monitoring_enabled", nullable = false)
    private boolean monitoringEnabled;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "monitoring_metrics", columnDefinition = "LONGTEXT")
    private String monitoringMetrics;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ToolBindingEntity() {
    }

    public ToolBindingEntity(
            UUID id,
            UUID projectId,
            String environment,
            String toolType,
            String profile,
            String encryptedConfiguration,
            boolean enabled,
            String summary,
            String tags,
            boolean monitoringEnabled,
            String monitoringMetrics,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.environment = environment;
        this.toolType = toolType;
        this.profile = profile;
        this.encryptedConfiguration = encryptedConfiguration;
        this.enabled = enabled;
        this.summary = summary;
        this.tags = tags;
        this.monitoringEnabled = monitoringEnabled;
        this.monitoringMetrics = monitoringMetrics;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getToolType() {
        return toolType;
    }

    public String getProfile() {
        return profile;
    }

    public String getEncryptedConfiguration() {
        return encryptedConfiguration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSummary() {
        return summary;
    }

    public String getTags() {
        return tags;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public String getMonitoringMetrics() {
        return monitoringMetrics;
    }

    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }

    public void setMonitoringMetrics(String monitoringMetrics) {
        this.monitoringMetrics = monitoringMetrics;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
