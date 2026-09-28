package com.maiya.investigator.mcp;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ReadOnlySqlGuard {
    private static final Pattern READ_PREFIX = Pattern.compile("^(select|show|describe|explain|with)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|replace|alter|drop|create|truncate|grant|revoke|call|load|outfile|for\\s+update)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ReadOnlySqlGuard() {
    }

    public static String normalize(String sql) {
        String normalized = sql == null ? "" : sql.trim().replaceAll(";+\\s*$", "");
        if (normalized.isBlank() || !READ_PREFIX.matcher(normalized).find() || FORBIDDEN.matcher(normalized).find()) {
            throw new IllegalArgumentException("仅允许只读 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH 查询");
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("不允许多语句查询");
        }
        if (!needsRowLimit(normalized) || hasLimit(normalized)) {
            return normalized;
        }
        return normalized + " LIMIT 200";
    }

    private static boolean needsRowLimit(String sql) {
        String head = sql.toLowerCase(Locale.ROOT);
        return head.startsWith("select") || head.startsWith("with");
    }

    private static boolean hasLimit(String sql) {
        return sql.toLowerCase(Locale.ROOT).matches("(?s).*\\blimit\\s+\\d+\\b.*");
    }
}
