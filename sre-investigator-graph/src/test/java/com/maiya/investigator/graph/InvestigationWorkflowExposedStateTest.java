package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.graph.state.InvestigationState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 回归：循环 Graph 内部累积器 InvestigationState 不得随对外 state 下发，
 * 否则 SSE / REST 序列化 RunOutcome 时会以「无属性 Bean」失败。
 */
class InvestigationWorkflowExposedStateTest {

    private static Map<String, Object> rawGraphState() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report", "## 结论\n- x");
        data.put("route", "http-5xx");
        data.put("usedToolCalls", 3);
        data.put("maxToolCalls", 16);
        data.put("budgetExhausted", false);
        // 内部累积器：question() 式访问器、无 getter，Jackson 无法序列化
        data.put("investigationState", new InvestigationState("为什么 5xx", "http-5xx", 16));
        return data;
    }

    @Test
    void exposedStateDropsInternalInvestigationState() {
        Map<String, Object> exposed = InvestigationWorkflow.exposedState(rawGraphState());
        assertFalse(exposed.containsKey("investigationState"), "对外 state 不应包含内部累积器");
        assertEquals("http-5xx", exposed.get("route"));
        assertEquals(3, exposed.get("usedToolCalls"));
    }

    @Test
    void exposedStateIsJacksonSerializable() {
        ObjectMapper mapper = new ObjectMapper();
        // 原始 state 含 InvestigationState，Spring 默认 ObjectMapper 会抛序列化异常（复现线上报错）
        assertThrows(Exception.class, () -> mapper.writeValueAsString(rawGraphState()));
        // 剔除后可正常序列化
        Map<String, Object> exposed = InvestigationWorkflow.exposedState(rawGraphState());
        assertDoesNotThrow(() -> mapper.writeValueAsString(exposed));
    }
}
