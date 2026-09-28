package com.maiya.investigator.graph.conversation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConversationMemoryCompactorTest {
    @Test
    void keepsIdsTablesAndLatestConclusion() {
        String summary = ConversationMemoryCompactor.compact(
                null,
                "userId=2010 看一下订单为什么没回调",
                "查了表 t_order。结论是支付回调没有更新状态。",
                600
        );
        assertTrue(summary.contains("userId=2010"));
        assertTrue(summary.contains("t_order"));
        assertTrue(summary.contains("结论："));
    }

    @Test
    void clipsFromTheHead() {
        String summary = ConversationMemoryCompactor.compact(
                null,
                "新问题",
                "结论" + "很长的字".repeat(80),
                80
        );
        assertTrue(summary.length() <= 80);
        assertFalse(summary.isBlank());
    }
}
