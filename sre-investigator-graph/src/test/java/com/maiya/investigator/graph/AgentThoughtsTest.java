package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentThoughtsTest {
    @Test
    void emitsToOpenSinkAndClipsLongText() {
        List<AgentThought> collected = new ArrayList<>();
        AgentThoughts.open(collected::add);
        try {
            AgentThoughts.emit("tool", "正在搜代码：" + "x".repeat(200));
        } finally {
            AgentThoughts.close();
        }
        assertEquals(1, collected.size());
        assertEquals("tool", collected.get(0).kind());
        assertTrue(collected.get(0).message().endsWith("…"));
        assertTrue(collected.get(0).message().length() <= 161);
    }

    @Test
    void ignoresBlankAfterClose() {
        List<AgentThought> collected = new ArrayList<>();
        AgentThoughts.open(collected::add);
        AgentThoughts.close();
        AgentThoughts.emit("tool", "不应再收到");
        assertTrue(collected.isEmpty());
    }

    @Test
    void fallsBackToSingleActiveSinkFromPoolThread() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> null).get();
            List<AgentThought> collected = new ArrayList<>();
            AgentThoughts.open(collected::add);
            try {
                pool.submit(() -> AgentThoughts.emit("tool", "跨线程工具步骤")).get();
            } finally {
                AgentThoughts.close();
            }
            assertEquals(1, collected.size());
            assertEquals("跨线程工具步骤", collected.get(0).message());
        } finally {
            pool.shutdownNow();
        }
    }
}
