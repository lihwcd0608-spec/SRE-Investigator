package com.maiya.investigator.boot.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FeishuTextTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void textContentIsJsonString() {
        String encoded = FeishuText.textContent(mapper, "hello");
        assertEquals("{\"text\":\"hello\"}", encoded);
        assertTrue(encoded.startsWith("{"));
    }

    @Test
    void textContentEscapesQuotes() {
        String encoded = FeishuText.textContent(mapper, "说\"hi\"");
        assertEquals("{\"text\":\"说\\\"hi\\\"\"}", encoded);
    }

    @Test
    void previewTruncatesLongText() {
        assertEquals("a".repeat(32) + "…", FeishuText.preview("a".repeat(50), 10));
        assertEquals("hello world", FeishuText.preview("hello   world"));
    }
}
