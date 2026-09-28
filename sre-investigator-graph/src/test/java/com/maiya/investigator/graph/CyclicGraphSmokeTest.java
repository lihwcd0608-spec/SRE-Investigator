package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * 验证图库确实接受"条件边自环"（loop → decide → loop/END）并能正常终止，
 * 与 InvestigationGraph 循环拓扑同款，防止 compile()/invoke() 在应用启动时才炸。
 */
class CyclicGraphSmokeTest {

    @Test
    void conditionalSelfLoopCompilesAndTerminates() throws Exception {
        OverAllState initial = OverAllStateBuilder.builder()
                .withKeyStrategy("count", new ReplaceStrategy())
                .build();
        StateGraph graph = new StateGraph(initial);
        graph.addNode("loop", state -> {
            int count = state.value("count", 0);
            return CompletableFuture.completedFuture(Map.of("count", count + 1));
        });
        graph.addEdge(StateGraph.START, "loop");
        graph.addConditionalEdges(
                "loop",
                AsyncEdgeAction.edge_async(state -> state.<Integer>value("count", 0) >= 3 ? "done" : "again"),
                Map.of("again", "loop", "done", StateGraph.END)
        );
        CompiledGraph compiled = graph.compile();

        OverAllState result = compiled.invoke(Map.of("count", 0))
                .orElseThrow(() -> new IllegalStateException("图未返回状态"));
        int count = result.value("count", 0);
        assertTrue(count >= 3, "循环应至少执行到计数达到阈值，实际=" + count);
        assertEquals(3, count);
    }
}
