package com.maiya.investigator.graph.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessFlowExecutorTest {
    @Test
    void composeQuestionIncludesPriorAndInstruction() {
        AgentFlow.Node node = new AgentFlow.Node(
                "n2",
                "看日志",
                "按 traceId 查 SLS",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                0,
                0
        );
        List<String> prior = new ArrayList<>();
        prior.add("核订单：\n订单已支付");
        String prompt = BusinessFlowExecutor.composeQuestion("订单超时", node, prior);
        assertTrue(prompt.contains("订单超时"));
        assertTrue(prompt.contains("看日志"));
        assertTrue(prompt.contains("按 traceId 查 SLS"));
        assertTrue(prompt.contains("订单已支付"));
        assertTrue(prompt.contains("只完成本步"));
    }
}
