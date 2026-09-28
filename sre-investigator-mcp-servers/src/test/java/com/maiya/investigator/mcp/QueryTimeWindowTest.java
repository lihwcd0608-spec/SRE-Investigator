package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryTimeWindowTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsToLastSevenDays() {
        QueryTimeWindow.Range range = QueryTimeWindow.resolve(null, null, clock);
        assertEquals(Instant.parse("2026-08-23T12:00:00Z").getEpochSecond(), range.fromEpochSec());
        assertEquals(Instant.parse("2026-08-30T12:00:00Z").getEpochSecond(), range.toEpochSec());
    }

    @Test
    void clampsSpanToSevenDays() {
        QueryTimeWindow.Range range = QueryTimeWindow.resolve("2026-08-01T00:00:00Z", "2026-08-30T12:00:00Z", clock);
        assertEquals(Duration.ofDays(7).toSeconds(), range.spanSeconds());
        assertEquals(Instant.parse("2026-08-23T12:00:00Z").getEpochSecond(), range.fromEpochSec());
    }

    @Test
    void acceptsUnixSeconds() {
        QueryTimeWindow.Range range = QueryTimeWindow.resolve("1756554300", "1756555200", clock);
        assertEquals(1756554300L, range.fromEpochSec());
        assertEquals(1756555200L, range.toEpochSec());
    }

    @Test
    void rejectsUnknownFormat() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> QueryTimeWindow.resolve("下周一下午", null, clock)
        );
        assertTrue(exception.getMessage().contains("无法解析"));
    }

    @Test
    void limitsRows() {
        assertEquals(50, QueryTimeWindow.limit(null, 50, 100));
        assertEquals(100, QueryTimeWindow.limit(999, 50, 100));
        assertEquals(20, QueryTimeWindow.limit(20, 50, 100));
    }

    @Test
    void acceptsNowAndRelativeLookback() {
        QueryTimeWindow.Range range = QueryTimeWindow.resolve("-7d", "now", clock);
        assertEquals(Instant.parse("2026-08-23T12:00:00Z").getEpochSecond(), range.fromEpochSec());
        assertEquals(Instant.parse("2026-08-30T12:00:00Z").getEpochSecond(), range.toEpochSec());
    }

    @Test
    void staleWhenEntireWindowOlderThanSevenDays() {
        QueryTimeWindow.Range range = QueryTimeWindow.resolve("2024-06-01 00:00:00", "2024-06-08 00:00:00", clock);
        assertTrue(QueryTimeWindow.stale(range, Instant.parse("2026-08-30T12:00:00Z")));
        assertEquals(1_717_171_200L, range.fromEpochSec());
        assertEquals(1_717_776_000L, range.toEpochSec());
    }

    @Test
    void defaultWindowIsNotStale() {
        QueryTimeWindow.Range range = QueryTimeWindow.resolve(null, null, clock);
        assertTrue(!QueryTimeWindow.stale(range, Instant.parse("2026-08-30T12:00:00Z")));
    }

    @Test
    void slicesNewestDayFirst() {
        QueryTimeWindow.Range range = QueryTimeWindow.resolve(null, null, clock);
        List<QueryTimeWindow.Range> slices = QueryTimeWindow.slices(range);
        assertEquals(7, slices.size());
        assertEquals(range.toEpochSec(), slices.get(0).toEpochSec());
        assertEquals(range.fromEpochSec(), slices.get(slices.size() - 1).fromEpochSec());
        assertEquals(Duration.ofHours(24).toSeconds(), slices.get(0).spanSeconds());
        assertEquals(Duration.ofHours(24).toSeconds(), slices.get(6).spanSeconds());
    }
}
