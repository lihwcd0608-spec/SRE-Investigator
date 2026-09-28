package com.maiya.investigator.boot.automation;

import com.maiya.investigator.domain.automation.ScheduleKind;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.scheduling.support.CronExpression;

public final class AutomationSchedules {
    private AutomationSchedules() {
    }

    public static Instant computeNext(ScheduleKind kind, String expr, String timezone, Instant now) {
        if (kind == null || expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("需要填写调度类型和表达式");
        }
        Instant cursor = now == null ? Instant.now() : now;
        return switch (kind) {
            case AT -> parseAt(expr, timezone, cursor);
            case EVERY -> cursor.plus(parseEvery(expr));
            case CRON -> nextCron(expr, timezone, cursor);
        };
    }

    public static Instant firstRunAt(ScheduleKind kind, String expr, String timezone, Instant now) {
        if (kind == ScheduleKind.AT) {
            Instant at = parseAbsolute(expr, timezone);
            return at.isAfter(now) ? at : now;
        }
        if (kind == ScheduleKind.EVERY) {
            return now.plus(parseEvery(expr));
        }
        return nextCron(expr, timezone, now);
    }

    public static void validate(ScheduleKind kind, String expr, String timezone) {
        firstRunAt(kind, expr, timezone, Instant.now());
    }

    static Duration parseEvery(String expr) {
        String raw = expr.trim().toLowerCase(Locale.ROOT);
        if (raw.matches("\\d+")) {
            raw = raw + "m";
        }
        if (!raw.matches("\\d+[smhd]")) {
            throw new IllegalArgumentException("间隔格式应为 5m / 1h / 1d");
        }
        long amount = Long.parseLong(raw.substring(0, raw.length() - 1));
        if (amount <= 0) {
            throw new IllegalArgumentException("间隔必须大于 0");
        }
        return switch (raw.charAt(raw.length() - 1)) {
            case 's' -> Duration.ofSeconds(Math.max(30, amount));
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("间隔格式应为 5m / 1h / 1d");
        };
    }

    private static Instant parseAt(String expr, String timezone, Instant now) {
        Instant at = parseAbsolute(expr, timezone);
        return at.isAfter(now) ? at : null;
    }

    private static Instant parseAbsolute(String expr, String timezone) {
        try {
            return Instant.parse(expr.trim());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(expr.trim()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return java.time.LocalDateTime.parse(expr.trim()).atZone(zone(timezone)).toInstant();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("一次性时间应为 ISO-8601，例如 2026-09-01T10:00:00+08:00");
        }
    }

    private static Instant nextCron(String expr, String timezone, Instant now) {
        CronExpression cron = CronExpression.parse(normalizeCron(expr));
        ZonedDateTime next = cron.next(now.atZone(zone(timezone)));
        if (next == null) {
            throw new IllegalArgumentException("cron 表达式没有下一次执行时间");
        }
        return next.toInstant();
    }

    static String normalizeCron(String expr) {
        String raw = expr.trim();
        String[] parts = raw.split("\\s+");
        if (parts.length == 5) {
            return "0 " + raw;
        }
        if (parts.length == 6) {
            return raw;
        }
        throw new IllegalArgumentException("cron 应为 5 或 6 段，例如 0 9 * * *");
    }

    private static ZoneId zone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("未知时区：" + timezone);
        }
    }
}
