package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolThoughtTest {
    @Test
    void startIncludesClippedDetailWithoutSecrets() {
        assertEquals("正在搜代码：UserMapper", ToolThought.start("code_search", "UserMapper"));
        assertTrue(ToolThought.start("mysql_query_readonly", "SELECT " + "x".repeat(120)).endsWith("…"));
    }

    @Test
    void hintSummarizesWithoutLeakingValues() {
        assertEquals("，返回 2 行", ToolThought.hint(List.of(Map.of("id", 1), Map.of("id", 2))));
        assertEquals("，命中 3 处", ToolThought.hint(Map.of("matches", List.of("a", "b", "c"))));
        assertEquals("，已读到值", ToolThought.hint(Map.of("key", "u:1", "value", "secret-token")));
        Map<String, Object> missing = new HashMap<>();
        missing.put("key", "u:1");
        missing.put("value", null);
        assertEquals("，key 不存在", ToolThought.hint(missing));
        assertFalse(ToolThought.hint(Map.of("key", "u:1", "value", "secret-token")).contains("secret"));
        assertEquals(
                "，读到 10-40 行（共 200 行）",
                ToolThought.hint(Map.of("startLine", 10, "endLine", 40, "totalLines", 200, "text", "class Foo"))
        );
        assertEquals("，失败：表不存在", ToolThought.hint(Map.of("ok", false, "error", "表不存在")));
    }
}
