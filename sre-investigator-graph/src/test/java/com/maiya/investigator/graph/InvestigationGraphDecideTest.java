package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 覆盖循环 Graph 的条件边 decideNext 的各分支。controlPlane 传 null：agentSlug 为空时不会触达。 */
class InvestigationGraphDecideTest {
    private static final int MAX_ITERATIONS = 3;

    private static OverAllState state(Map<String, Object> values) {
        Map<String, Object> base = new HashMap<>();
        base.put("modelUsed", true);
        base.put("budgetExhausted", false);
        base.put("iteration", 1);
        base.put("routeSource", "MODEL");
        base.put("mcpProfile", "");
        base.put("projectId", "");
        base.put("agentSlug", "");
        base.put("investigation", "证据不足，还需继续");
        base.putAll(values);
        return new OverAllState(base);
    }

    @Test
    void continuesWhenEvidenceInsufficient() {
        assertEquals("continue", InvestigationGraph.decideNext(state(Map.of()), null, MAX_ITERATIONS));
    }

    @Test
    void doneWhenModelFailed() {
        assertEquals("done", InvestigationGraph.decideNext(state(Map.of("modelUsed", false)), null, MAX_ITERATIONS));
    }

    @Test
    void doneWhenBudgetExhausted() {
        assertEquals("done", InvestigationGraph.decideNext(state(Map.of("budgetExhausted", true)), null, MAX_ITERATIONS));
    }

    @Test
    void doneWhenIterationCapReached() {
        assertEquals("done", InvestigationGraph.decideNext(state(Map.of("iteration", 3)), null, MAX_ITERATIONS));
    }

    @Test
    void doneWhenNamedMcp() {
        assertEquals("done", InvestigationGraph.decideNext(state(Map.of("routeSource", "NAMED_MCP")), null, MAX_ITERATIONS));
        assertEquals("done", InvestigationGraph.decideNext(state(Map.of("mcpProfile", "prod")), null, MAX_ITERATIONS));
    }

    @Test
    void doneWhenReportProduced() {
        assertEquals("done",
                InvestigationGraph.decideNext(state(Map.of("investigation", "## 已确认的证据\n- x")), null, MAX_ITERATIONS));
    }
}
