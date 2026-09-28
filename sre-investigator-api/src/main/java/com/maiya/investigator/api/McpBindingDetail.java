package com.maiya.investigator.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record McpBindingDetail(
        UUID id,
        UUID projectId,
        Environment environment,
        String toolKind,
        String profile,
        boolean enabled,
        boolean configured,
        boolean passwordSet,
        Map<String, Object> configuration,
        String summary,
        String tags,
        Instant createdAt,
        Instant updatedAt
) {
}
