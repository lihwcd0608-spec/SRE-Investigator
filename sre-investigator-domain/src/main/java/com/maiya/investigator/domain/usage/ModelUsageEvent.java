package com.maiya.investigator.domain.usage;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ModelUsageEvent(
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
    public static ModelUsageEvent of(
            UUID userId,
            String username,
            String roleCodes,
            UUID projectId,
            String provider,
            String model,
            Long promptTokens,
            Long completionTokens,
            Long totalTokens
    ) {
        return new ModelUsageEvent(
                UUID.randomUUID(),
                Instant.now(),
                userId,
                username == null ? "" : username,
                roleCodes == null ? "" : roleCodes,
                projectId,
                provider,
                model,
                1,
                promptTokens,
                completionTokens,
                totalTokens
        );
    }

    public List<String> roleCodeList() {
        if (roleCodes == null || roleCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roleCodes.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
