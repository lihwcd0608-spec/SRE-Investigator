package com.maiya.investigator.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模型调用 mongodb_query_readonly 时，filter/projection/sort 可能是 JSON 字符串，也可能是 JSON 对象。
 */
public final class MongoJsonArg {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MongoJsonArg() {
    }

    public static String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text.isBlank() ? null : text;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("必须是 JSON 对象");
        }
    }
}
