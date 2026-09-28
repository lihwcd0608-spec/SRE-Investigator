package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolBudgetTest {
    @Test
    void marksExhaustedOnlyAfterTheLimitIsExceeded() {
        InvestigationAgent.ToolBudget budget = new InvestigationAgent.ToolBudget(2);
        budget.consume();
        budget.consume();
        assertEquals(2, budget.used());
        assertFalse(budget.exhausted());
        IllegalStateException exception = assertThrows(IllegalStateException.class, budget::consume);
        assertTrue(budget.exhausted());
        assertTrue(exception.getMessage().contains("已达到只读工具调用预算"));
    }
}
