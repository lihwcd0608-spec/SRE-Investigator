package com.maiya.investigator.boot.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FeishuText {
    private static final int MAX_CHUNK = 3800;

    private FeishuText() {
    }

    static String textContent(ObjectMapper mapper, String text) {
        try {
            return mapper.writeValueAsString(Map.of("text", text == null ? "" : text));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化飞书文本失败");
        }
    }

    static String preview(String text) {
        return preview(text, 240);
    }

    static String preview(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        int limit = Math.max(32, maxChars);
        return compact.length() > limit ? compact.substring(0, limit) + "…" : compact;
    }

    static String stripMentions(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("@\\S+", " ").replaceAll("\\s+", " ").trim();
    }

    static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            chunks.add("（空）");
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + MAX_CHUNK);
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
