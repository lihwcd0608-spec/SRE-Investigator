package com.maiya.investigator.boot.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.usage.ModelUsageEvent;
import com.maiya.investigator.domain.usage.UsageReport;
import com.maiya.investigator.infra.InMemoryModelUsageStore;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsageQueryServiceTest {
    @Test
    void summarizesPersistedEventsInRange() {
        InMemoryModelUsageStore store = new InMemoryModelUsageStore();
        store.save(ModelUsageEvent.of(
                UUID.randomUUID(),
                "alice",
                "OPERATOR",
                null,
                "dashscope",
                "qwen-plus",
                8L,
                2L,
                10L
        ));
        UsageQueryService service = new UsageQueryService(store);

        UsageReport report = service.summarize(LocalDate.now(), LocalDate.now());

        assertEquals(1, report.totals().callCount());
        assertEquals(10, report.totals().totalTokens());
        assertEquals("alice", report.byUser().get(0).username());
    }

    @Test
    void rejectsInvertedRange() {
        UsageQueryService service = new UsageQueryService(new InMemoryModelUsageStore());
        assertThrows(IllegalArgumentException.class, () -> service.summarize(LocalDate.now(), LocalDate.now().minusDays(1)));
    }

    @Test
    void defaultsToLastThirtyDays() {
        InMemoryModelUsageStore store = new InMemoryModelUsageStore();
        store.save(ModelUsageEvent.of(
                UUID.randomUUID(),
                "bob",
                "VIEWER",
                null,
                "openai",
                "gpt-4o-mini",
                null,
                null,
                null
        ));
        UsageReport report = new UsageQueryService(store).summarize(null, null);
        assertEquals(LocalDate.now().minusDays(29), report.from());
        assertEquals(LocalDate.now(), report.to());
        assertEquals(1, report.totals().callCount());
        assertTrue(report.totals().tokenIncomplete());
    }
}
