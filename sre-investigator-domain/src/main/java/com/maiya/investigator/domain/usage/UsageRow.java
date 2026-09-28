package com.maiya.investigator.domain.usage;

public record UsageRow(
        String userId,
        String username,
        String roleCode,
        String provider,
        String model,
        long callCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        boolean tokenIncomplete
) {
}
