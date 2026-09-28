package com.maiya.investigator.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import org.bson.Document;

final class MongoReadGuard {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> USER_ID_ALIASES = Set.of(
            "userid",
            "user_id",
            "uid",
            "p_user_id"
    );

    private MongoReadGuard() {
    }

    static String identifier(String raw, String label) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        if (!value.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(label + " 只能包含字母、数字、点、下划线和短横线");
        }
        return value;
    }

    static Document filter(String raw) {
        if (raw == null || raw.isBlank() || "{}".equals(raw.trim())) {
            return new Document();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalArgumentException("filter 必须是 JSON 对象");
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("filter 必须是 JSON 对象");
        }
        rejectWriteOps(node, "filter");
        return Document.parse(node.toString());
    }

    static Document normalizeQuery(Document filter, String collection) {
        if (filter == null || filter.isEmpty()) {
            return filter == null ? new Document() : filter;
        }
        Document normalized = new Document(filter);
        if (normalized.containsKey("_id")) {
            normalized.put("_id", coerceNumericId(normalized.get("_id")));
        }
        if (isUserPortraitCollection(collection) && normalized.size() == 1) {
            String onlyKey = normalized.keySet().iterator().next();
            if (isUserIdAlias(onlyKey) && !"_id".equals(onlyKey)) {
                Object value = coerceNumericId(normalized.get(onlyKey));
                return new Document("_id", value);
            }
        }
        return normalized;
    }

    static Document document(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalArgumentException(label + " 必须是 JSON 对象");
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(label + " 必须是 JSON 对象");
        }
        rejectWriteOps(node, label);
        return Document.parse(node.toString());
    }

    private static boolean isUserPortraitCollection(String collection) {
        if (collection == null || collection.isBlank()) {
            return false;
        }
        String normalized = collection.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("user_tag_attr");
    }

    private static boolean isUserIdAlias(String field) {
        if (field == null || field.isBlank()) {
            return false;
        }
        String normalized = field.trim().toLowerCase(Locale.ROOT);
        return USER_ID_ALIASES.contains(normalized) || "userid".equals(normalized.replace("_", ""));
    }

    private static Object coerceNumericId(Object value) {
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.matches("\\d{5,}")) {
                try {
                    return Long.parseLong(trimmed);
                } catch (NumberFormatException ignored) {
                    return value;
                }
            }
        }
        return value;
    }

    private static void rejectWriteOps(JsonNode node, String label) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String key = name.toLowerCase(Locale.ROOT);
                if ("$where".equals(key) || "$function".equals(key) || "$accumulator".equals(key)
                        || "$out".equals(key) || "$merge".equals(key)) {
                    throw new IllegalArgumentException(label + " 不允许使用 " + name);
                }
                rejectWriteOps(node.get(name), label);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                rejectWriteOps(child, label);
            }
        }
    }
}
