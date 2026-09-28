package com.maiya.investigator.graph.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InvestigationStateTest {

    @Test
    void dedupesEvidenceByKey() {
        InvestigationState state = new InvestigationState("Q", "http-5xx", 16);
        assertTrue(state.addEvidence(EvidenceItem.of("mysql_query_readonly", "订单 123 状态=PAID")));
        assertFalse(state.addEvidence(EvidenceItem.of("mysql_query_readonly", "订单 123 状态=PAID")));
        assertEquals(1, state.evidence().size());
    }

    @Test
    void tracksToolLedgerDedupeAndBudget() {
        InvestigationState state = new InvestigationState("Q", "http-5xx", 5);
        assertTrue(state.recordToolCall(new ToolCallRecord("mysql_query_readonly", "select 1", true, "1 行")));
        assertFalse(state.recordToolCall(new ToolCallRecord("mysql_query_readonly", "select 1", true, "1 行")));
        assertTrue(state.alreadyCalled("mysql_query_readonly", "select 1"));
        assertEquals(2, state.budgetUsed());
        assertEquals(3, state.remainingBudget());
        assertFalse(state.budgetExhausted());
    }

    @Test
    void exhaustsBudget() {
        InvestigationState state = new InvestigationState("Q", "http-5xx", 2);
        state.addBudgetUsed(2);
        assertTrue(state.budgetExhausted());
        assertEquals(0, state.remainingBudget());
    }

    @Test
    void strongEvidenceDetected() {
        InvestigationState state = new InvestigationState("Q", "http-5xx", 16);
        state.addEvidence(new EvidenceItem("code", "线索", Confidence.LOW));
        assertFalse(state.hasStrongEvidence());
        state.addEvidence(new EvidenceItem("sls_query_readonly", "网关 500 命中", Confidence.HIGH));
        assertTrue(state.hasStrongEvidence());
    }

    @Test
    void contextIncludesPriorRoundsAndLedger() {
        InvestigationState state = new InvestigationState("Q", "http-5xx", 16);
        state.recordRound(1, "第一轮读代码定位到表 t_order");
        state.recordToolCall(new ToolCallRecord("mysql_query_readonly", "select * from t_order", true, "3 行"));
        String context = state.toContext();
        assertTrue(context.contains("此前轮次结论"));
        assertTrue(context.contains("t_order"));
        assertTrue(context.contains("已调用工具"));
        assertTrue(context.contains("预算"));
    }

    @Test
    void iterationCounterAdvances() {
        InvestigationState state = new InvestigationState("Q", "http-5xx", 16);
        assertEquals(1, state.nextIteration());
        assertEquals(2, state.nextIteration());
        assertEquals(2, state.iterations());
    }
}
