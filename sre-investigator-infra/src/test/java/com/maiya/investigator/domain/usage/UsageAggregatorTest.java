package com.maiya.investigator.domain.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsageAggregatorTest {
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void aggregatesByUserRoleAndModel() {
        ModelUsageEvent first = event(ALICE, "alice", "SUPER_ADMIN,OPERATOR", "dashscope", "qwen-plus", 10L, 4L, 14L);
        ModelUsageEvent second = event(ALICE, "alice", "SUPER_ADMIN,OPERATOR", "dashscope", "qwen-plus", 6L, 2L, 8L);
        ModelUsageEvent third = event(BOB, "bob", "VIEWER", "openai", "gpt-4o-mini", 3L, 1L, 4L);

        UsageReport report = UsageAggregator.aggregate(List.of(first, second, third), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 28));

        assertEquals(3, report.totals().callCount());
        assertEquals(26, report.totals().totalTokens());
        assertFalse(report.totals().tokenIncomplete());
        assertEquals(2, report.byUser().size());
        assertEquals(2, report.byUser().get(0).callCount());
        assertEquals("alice", report.byUser().get(0).username());
        assertEquals(22, report.byUser().get(0).totalTokens());
        assertEquals(3, report.byRole().size());
        assertEquals(2, report.byRole().stream().filter(row -> "SUPER_ADMIN".equals(row.roleCode())).findFirst().orElseThrow().callCount());
        assertEquals(2, report.byRole().stream().filter(row -> "OPERATOR".equals(row.roleCode())).findFirst().orElseThrow().callCount());
        assertEquals(2, report.byModel().size());
        assertEquals(2, report.byModel().get(0).callCount());
    }

    @Test
    void missingTokensStillCountCalls() {
        ModelUsageEvent event = event(ALICE, "alice", "VIEWER", "ollama", "qwen2.5", null, null, null);

        UsageReport report = UsageAggregator.aggregate(List.of(event), LocalDate.now(), LocalDate.now());

        assertEquals(1, report.totals().callCount());
        assertEquals(0, report.totals().totalTokens());
        assertTrue(report.totals().tokenIncomplete());
        assertTrue(report.byUser().get(0).tokenIncomplete());
        assertTrue(report.byRole().get(0).tokenIncomplete());
        assertTrue(report.byModel().get(0).tokenIncomplete());
    }

    private static ModelUsageEvent event(
            UUID userId,
            String username,
            String roles,
            String provider,
            String model,
            Long prompt,
            Long completion,
            Long total
    ) {
        return new ModelUsageEvent(
                UUID.randomUUID(),
                Instant.parse("2026-08-20T10:00:00Z"),
                userId,
                username,
                roles,
                null,
                provider,
                model,
                1,
                prompt,
                completion,
                total
        );
    }
}
