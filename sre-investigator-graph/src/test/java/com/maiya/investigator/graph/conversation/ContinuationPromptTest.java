package com.maiya.investigator.graph.conversation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ContinuationPromptTest {
    @Test
    void appendsPriorReportForContinuation() {
        String prompt = ContinuationPrompt.append("继续看强插", "预约剧没生效", "已确认入口类，表名待查");
        assertTrue(prompt.contains("用户确认继续排查"));
        assertTrue(prompt.contains("不要重复"));
        assertTrue(prompt.contains("已确认入口类，表名待查"));
    }

    @Test
    void detectsBudgetExhaustedFromState() {
        ConversationOutcome exhausted = new ConversationOutcome("部分结论", true, "http-5xx", Map.of(
                "budgetExhausted", true,
                "usedToolCalls", 100,
                "maxToolCalls", 100
        ));
        assertTrue(exhausted.budgetExhausted());
        assertTrue(exhausted.usedToolCalls() == 100);
        assertFalse(new ConversationOutcome("ok", true, "ask", Map.of("budgetExhausted", false)).budgetExhausted());
    }
}
