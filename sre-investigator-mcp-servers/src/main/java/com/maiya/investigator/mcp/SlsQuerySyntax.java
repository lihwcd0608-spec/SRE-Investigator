package com.maiya.investigator.mcp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SLS 查询语法兼容。
 *
 * <p>线上 logstore 常见两种形态：
 * <ul>
 *   <li>已建键值索引（如 demo-app-video-query 的 {@code traceId}）：应使用 {@code traceId: <值>}</li>
 *   <li>未建键值索引：{@code field: value} 会 400，需改用全文碎片 {@code field\":\"value} 或短语</li>
 * </ul>
 * 模型常被旧提示写成 {@code traceId\":\"...}，在已建索引的库上会全文零命中。
 * 因此这里产出一组候选，由 {@link ReadonlySlsTool} 依次尝试直到有结果。
 */
final class SlsQuerySyntax {
    private static final Pattern FIELD_COLON = Pattern.compile(
            "(?<![\"\\w])([A-Za-z_][A-Za-z0-9_.-]*)\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^\\s()]+)"
    );
    private static final Pattern FIELD_EQUALS = Pattern.compile(
            "(?<![\"\\w])([A-Za-z_][A-Za-z0-9_.-]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^\\s()]+)"
    );
    private static final Pattern JSON_FRAGMENT = Pattern.compile(
            "(?<![\"\\w])([A-Za-z_][A-Za-z0-9_.-]*)\\\\?\":\\\\?\"([^\"\\s]+)\""
    );
    // 兼容旧 normalize 产物：traceId\":\"abc（右侧没有闭合引号）
    private static final Pattern JSON_FRAGMENT_LOOSE = Pattern.compile(
            "(?<![\"\\w])([A-Za-z_][A-Za-z0-9_.-]*)\":\"([^\"\\s]+)"
    );
    private static final Pattern HEX_ID = Pattern.compile("(?<![A-Fa-f0-9])([A-Fa-f0-9]{32})(?![A-Fa-f0-9])");
    private static final Pattern FIELD_NAME = Pattern.compile(
            "(?i)trace[_-]?id|request[_-]?id|rpc[_-]?id|eagleeye[-_]?traceid|user[_-]?id"
    );

    private SlsQuerySyntax() {
    }

    /** 规范化：把 field=value 收成 field: value；其余原样（含已是 KV / 裸 id / 旧碎片写法）。 */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String query = raw.trim();
        if (isPhrase(query) || looksLikeJsonFragment(query)) {
            return query;
        }
        Matcher equals = FIELD_EQUALS.matcher(query);
        if (equals.find()) {
            StringBuffer rewritten = new StringBuffer();
            equals.reset();
            while (equals.find()) {
                String key = equals.group(1);
                String value = unwrapQuotes(equals.group(2));
                equals.appendReplacement(rewritten, Matcher.quoteReplacement(key + ": " + value));
            }
            equals.appendTail(rewritten);
            return rewritten.toString();
        }
        return query;
    }

    /**
     * 生成按优先级排列的查询候选：标准 KV → JSON 碎片 → 裸值 → 短语。
     * 模型把类名 AND 进 query 时，只保留裸 id / 标准 KV（控制台能命中的写法）。
     */
    static List<String> candidates(String raw) {
        String primary = normalize(raw);
        String hex = extractHexId(raw);
        Set<String> ordered = new LinkedHashSet<>();
        if (hex != null && noisy(raw, hex)) {
            addHexQueries(ordered, hex);
            return new ArrayList<>(ordered);
        }
        if (!primary.isBlank()) {
            ordered.add(primary);
        }
        FieldValue field = extractFieldValue(raw);
        if (field != null) {
            ordered.add(field.key() + ": " + field.value());
            ordered.add(field.key() + "\":\"" + field.value());
            ordered.add(field.value());
            ordered.add(asPhrase(field.key() + ": " + field.value()));
            ordered.add(asPhrase(field.value()));
        } else if (!primary.isBlank() && !isPhrase(primary)) {
            ordered.add(asPhrase(primary));
        }
        if (hex != null) {
            addHexQueries(ordered, hex);
        }
        return new ArrayList<>(ordered);
    }

    static String extractHexId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = HEX_ID.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    static boolean noisy(String raw, String hex) {
        if (raw == null || hex == null || hex.isBlank()) {
            return false;
        }
        String leftover = raw.replace(hex, " ");
        leftover = FIELD_NAME.matcher(leftover).replaceAll(" ");
        leftover = leftover.replaceAll("[\\\\\"':=()\\[\\]{},]", " ");
        leftover = leftover.replaceAll("(?i)\\b(and|or|not)\\b", " ");
        leftover = leftover.replaceAll("\\s+", "");
        return leftover.length() >= 3;
    }

    private static void addHexQueries(Set<String> ordered, String hex) {
        String lower = hex.toLowerCase(Locale.ROOT);
        ordered.add(lower);
        ordered.add("traceId: " + lower);
        ordered.add("trace_id: " + lower);
    }

    static boolean isPhrase(String query) {
        return query != null && query.length() >= 2 && query.startsWith("\"") && query.endsWith("\"");
    }

    static String asPhrase(String raw) {
        String query = raw == null ? "" : raw.trim();
        if (isPhrase(query)) {
            return query;
        }
        return "\"" + query.replace("\"", "\\\"") + "\"";
    }

    static boolean needsPhraseFallback(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("ParameterInvalid") || message.contains("not config as key value")
                || message.contains("wrap : with quotation");
    }

    static boolean looksLikeJsonFragment(String query) {
        return query != null && (JSON_FRAGMENT.matcher(query).find() || JSON_FRAGMENT_LOOSE.matcher(query).find());
    }

    private static FieldValue extractFieldValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String query = raw.trim();
        Matcher json = JSON_FRAGMENT.matcher(query);
        if (json.find()) {
            return new FieldValue(json.group(1), unwrapQuotes(json.group(2)));
        }
        Matcher loose = JSON_FRAGMENT_LOOSE.matcher(query);
        if (loose.find()) {
            return new FieldValue(loose.group(1), unwrapQuotes(loose.group(2)));
        }
        Matcher colon = FIELD_COLON.matcher(query);
        if (colon.find()) {
            return new FieldValue(colon.group(1), unwrapQuotes(colon.group(2)));
        }
        Matcher equals = FIELD_EQUALS.matcher(query);
        if (equals.find()) {
            return new FieldValue(equals.group(1), unwrapQuotes(equals.group(2)));
        }
        return null;
    }

    private static String unwrapQuotes(String value) {
        if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record FieldValue(String key, String value) {
    }
}
