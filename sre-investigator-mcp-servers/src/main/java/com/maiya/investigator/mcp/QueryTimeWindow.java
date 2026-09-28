package com.maiya.investigator.mcp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryTimeWindow {
    /** from/to 都空时：默认回看近 7 天（排查 trace 常跨小时/天）。 */
    public static final Duration DEFAULT_SPAN = Duration.ofDays(7);
    /** 显式 from/to 跨度上限，与默认一致，避免无界全库扫。 */
    public static final Duration MAX_SPAN = Duration.ofDays(7);
    /**
     * GetLogs 单次时间窗。跨度过大时首次常返回 Incomplete 空数组，按 24h 从近到远分段更稳。
     */
    public static final Duration SLICE = Duration.ofHours(24);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern RELATIVE = Pattern.compile(
            "^(?:now\\s*-\\s*)?-?(\\d+)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d)(?:\\s+ago)?$",
            Pattern.CASE_INSENSITIVE
    );

    private QueryTimeWindow() {
    }

    public record Range(long fromEpochSec, long toEpochSec) {
        public long spanSeconds() {
            return Math.max(0, toEpochSec - fromEpochSec);
        }
    }

    public static Range resolve(String from, String to) {
        return resolve(from, to, Clock.systemDefaultZone());
    }

    public static Range resolve(String from, String to, Clock clock) {
        Instant now = Instant.now(clock);
        Instant parsedTo = parse(to, now, now, true);
        Instant parsedFrom = parse(from, parsedTo.minus(DEFAULT_SPAN), now, false);
        if (!parsedFrom.isBefore(parsedTo)) {
            parsedFrom = parsedTo.minus(DEFAULT_SPAN);
        }
        if (Duration.between(parsedFrom, parsedTo).compareTo(MAX_SPAN) > 0) {
            parsedFrom = parsedTo.minus(MAX_SPAN);
        }
        return new Range(parsedFrom.getEpochSecond(), parsedTo.getEpochSecond());
    }

    public static Range recent(Clock clock) {
        return resolve(null, null, clock);
    }

    /** 从近到远切成不超过 24h 的段；单段窗口原样返回。 */
    public static List<Range> slices(Range window) {
        return slices(window, SLICE);
    }

    public static List<Range> slices(Range window, Duration size) {
        List<Range> out = new ArrayList<>();
        if (window == null || window.toEpochSec() <= window.fromEpochSec()) {
            return out;
        }
        long span = size == null || size.isZero() || size.isNegative()
                ? SLICE.toSeconds()
                : Math.max(1L, size.toSeconds());
        long cursorTo = window.toEpochSec();
        long start = window.fromEpochSec();
        while (cursorTo > start) {
            long cursorFrom = Math.max(start, cursorTo - span);
            out.add(new Range(cursorFrom, cursorTo));
            cursorTo = cursorFrom;
        }
        return out;
    }

    /**
     * 时间窗整体落在「现在 − 7 天」之前：常见于模型把年份编成训练数据里的 2024。
     * 用户未给明确历史日期时，空结果应回退近 7 天。
     */
    public static boolean stale(Range window, Instant now) {
        if (window == null || now == null) {
            return false;
        }
        return Instant.ofEpochSecond(window.toEpochSec()).isBefore(now.minus(MAX_SPAN));
    }

    public static boolean stale(Range window) {
        return stale(window, Instant.now());
    }

    public static int limit(Integer requested, int fallback, int max) {
        if (requested == null || requested <= 0) {
            return fallback;
        }
        return Math.min(requested, max);
    }

    private static Instant parse(String raw, Instant fallback, Instant now, boolean endExclusive) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if ("now".equals(lower) || "now()".equals(lower)) {
            return now;
        }
        if ("today".equals(lower)) {
            return endExclusive ? now : startOfDay(now);
        }
        if ("yesterday".equals(lower)) {
            Instant start = startOfDay(now).minus(Duration.ofDays(1));
            return endExclusive ? startOfDay(now) : start;
        }
        Matcher relative = RELATIVE.matcher(value);
        if (relative.matches()) {
            return now.minus(duration(relative.group(1), relative.group(2)));
        }
        if (value.matches("\\d{10}")) {
            return Instant.ofEpochSecond(Long.parseLong(value));
        }
        if (value.matches("\\d{13}")) {
            return Instant.ofEpochMilli(Long.parseLong(value));
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate date = LocalDate.parse(value);
            if (endExclusive) {
                return date.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
            }
            return date.atStartOfDay(SHANGHAI).toInstant();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value, LOCAL).atZone(SHANGHAI).toInstant();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "无法解析时间：" + raw + "。可用 now、-1h、-7d、unix 秒、毫秒、ISO-8601 或 yyyy-MM-dd[ HH:mm:ss]"
            );
        }
    }

    private static Instant startOfDay(Instant instant) {
        return instant.atZone(SHANGHAI).toLocalDate().atStartOfDay(SHANGHAI).toInstant();
    }

    private static Duration duration(String amount, String unit) {
        long n = Long.parseLong(amount);
        String u = unit.toLowerCase(Locale.ROOT);
        if (u.equals("s") || u.startsWith("sec")) {
            return Duration.ofSeconds(n);
        }
        if (u.equals("m") || u.startsWith("min")) {
            return Duration.ofMinutes(n);
        }
        if (u.equals("h") || u.startsWith("hr") || u.startsWith("hour")) {
            return Duration.ofHours(n);
        }
        return Duration.ofDays(n);
    }
}
