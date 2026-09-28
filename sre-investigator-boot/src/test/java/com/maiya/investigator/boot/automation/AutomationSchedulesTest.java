package com.maiya.investigator.boot.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.automation.ScheduleKind;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class AutomationSchedulesTest {
    @Test
    void everyAddsIntervalAndDoesNotFireImmediately() {
        Instant now = Instant.parse("2026-08-31T03:00:00Z");
        Instant next = AutomationSchedules.firstRunAt(ScheduleKind.EVERY, "15m", "Asia/Shanghai", now);
        assertEquals(now.plus(Duration.ofMinutes(15)), next);
        assertEquals(Duration.ofHours(1), AutomationSchedules.parseEvery("1h"));
        assertEquals(Duration.ofDays(1), AutomationSchedules.parseEvery("1d"));
        assertEquals(Duration.ofMinutes(5), AutomationSchedules.parseEvery("5"));
    }

    @Test
    void fiveFieldCronGetsSecondsPrefix() {
        assertEquals("0 0 9 * * *", AutomationSchedules.normalizeCron("0 9 * * *"));
        Instant now = ZonedDateTime.of(2026, 8, 31, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai")).toInstant();
        Instant next = AutomationSchedules.firstRunAt(ScheduleKind.CRON, "0 9 * * *", "Asia/Shanghai", now);
        ZonedDateTime shanghai = next.atZone(ZoneId.of("Asia/Shanghai"));
        assertEquals(9, shanghai.getHour());
        assertEquals(0, shanghai.getMinute());
        assertTrue(next.isAfter(now));
    }

    @Test
    void atUsesAbsoluteTime() {
        Instant now = Instant.parse("2026-08-31T01:00:00Z");
        Instant next = AutomationSchedules.firstRunAt(
                ScheduleKind.AT,
                "2026-09-01T10:00:00+08:00",
                "Asia/Shanghai",
                now
        );
        assertEquals(Instant.parse("2026-09-01T02:00:00Z"), next);
        assertNull(AutomationSchedules.computeNext(
                ScheduleKind.AT,
                "2026-08-31T08:00:00+08:00",
                "Asia/Shanghai",
                now
        ));
    }

    @Test
    void rejectsInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> AutomationSchedules.parseEvery("weekly"));
        assertThrows(IllegalArgumentException.class, () -> AutomationSchedules.normalizeCron("* * *"));
        assertThrows(IllegalArgumentException.class, () -> AutomationSchedules.validate(
                ScheduleKind.AT,
                "明天上午",
                "Asia/Shanghai"
        ));
        assertThrows(IllegalArgumentException.class, () -> AutomationSchedules.validate(
                ScheduleKind.CRON,
                "0 9 * * *",
                "Not/AZone"
        ));
    }
}
