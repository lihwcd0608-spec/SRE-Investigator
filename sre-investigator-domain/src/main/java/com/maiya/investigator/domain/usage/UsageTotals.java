package com.maiya.investigator.domain.usage;

public record UsageTotals(
        long callCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        boolean tokenIncomplete
) {
}
