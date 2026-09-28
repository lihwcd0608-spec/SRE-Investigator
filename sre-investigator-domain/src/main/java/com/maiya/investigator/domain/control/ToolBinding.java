package com.maiya.investigator.domain.control;

import com.maiya.investigator.api.Environment;
import java.time.Instant;
import java.util.UUID;

public record ToolBinding(
        UUID id,
        UUID projectId,
        Environment environment,
        ToolKind toolKind,
        String profile,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        boolean configured,
        String summary,
        String tags,
        boolean monitoringEnabled,
        String monitoringMetrics
) {
    public static ToolBinding create(
            UUID projectId,
            Environment environment,
            ToolKind toolKind,
            String profile,
            Instant now
    ) {
        return new ToolBinding(
                UUID.randomUUID(), projectId, environment, toolKind, profile,
                true, now, now, false, null, null, false, null
        );
    }

    public ToolBinding withConfigured(boolean configured) {
        return new ToolBinding(
                id, projectId, environment, toolKind, profile, enabled,
                createdAt, updatedAt, configured, summary, tags,
                monitoringEnabled, monitoringMetrics
        );
    }

    public ToolBinding withEnabled(boolean enabled, Instant updatedAt) {
        return new ToolBinding(
                id, projectId, environment, toolKind, profile, enabled,
                createdAt, updatedAt, configured, summary, tags,
                monitoringEnabled, monitoringMetrics
        );
    }

    public ToolBinding withDetails(
            Environment environment,
            ToolKind toolKind,
            String profile,
            Instant updatedAt,
            boolean configured,
            String summary,
            String tags
    ) {
        return new ToolBinding(
                id, projectId, environment, toolKind, profile, enabled,
                createdAt, updatedAt, configured, summary, tags,
                monitoringEnabled, monitoringMetrics
        );
    }

    public ToolBinding withMonitoring(boolean monitoringEnabled, String monitoringMetrics, Instant updatedAt) {
        return new ToolBinding(
                id, projectId, environment, toolKind, profile, enabled,
                createdAt, updatedAt, configured, summary, tags,
                monitoringEnabled, monitoringMetrics
        );
    }
}
