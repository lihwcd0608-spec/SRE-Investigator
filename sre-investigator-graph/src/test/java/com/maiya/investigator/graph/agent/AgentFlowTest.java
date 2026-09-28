package com.maiya.investigator.graph.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentFlowTest {
    @Test
    void emptyEdgesKeepListOrder() {
        AgentFlow flow = new AgentFlow(nodes("n1", "n2", "n3"), new ArrayList<>());
        assertEquals(List.of("n1", "n2", "n3"), ids(flow.topologicalOrder()));
        assertFalse(flow.cyclic());
    }

    @Test
    void dagFollowsEdges() {
        List<AgentFlow.Edge> edges = new ArrayList<>();
        edges.add(new AgentFlow.Edge("n1", "n2"));
        edges.add(new AgentFlow.Edge("n2", "n3"));
        AgentFlow flow = new AgentFlow(nodes("n3", "n1", "n2"), edges);
        assertEquals(List.of("n1", "n2", "n3"), ids(flow.topologicalOrder()));
    }

    @Test
    void cycleThrows() {
        List<AgentFlow.Edge> edges = new ArrayList<>();
        edges.add(new AgentFlow.Edge("n1", "n2"));
        edges.add(new AgentFlow.Edge("n2", "n1"));
        AgentFlow flow = new AgentFlow(nodes("n1", "n2"), edges);
        assertTrue(flow.cyclic());
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, flow::topologicalOrder);
        assertTrue(thrown.getMessage().contains("环"));
    }

    @Test
    void selfLoopIsCycle() {
        List<AgentFlow.Edge> edges = new ArrayList<>();
        edges.add(new AgentFlow.Edge("n1", "n1"));
        AgentFlow flow = new AgentFlow(nodes("n1"), edges);
        assertTrue(flow.cyclic());
    }

    @Test
    void pinProfileOnlyWhenNodeHasSingleKind() {
        List<AgentFlow.Mcp> mcps = new ArrayList<>();
        mcps.add(new AgentFlow.Mcp(ToolKind.MYSQL, "zg_mysql_test"));
        AgentFlow.Node mysqlOnly = new AgentFlow.Node(
                "n1",
                "查库",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                mcps,
                0,
                0
        );
        assertEquals("zg_mysql_test", mysqlOnly.pinnedProfileOrBlank());
        List<ToolKind> kinds = new ArrayList<>();
        kinds.add(ToolKind.CODE);
        AgentFlow.Node withCode = new AgentFlow.Node(
                "n2",
                "对照代码",
                "",
                kinds,
                new ArrayList<>(),
                mcps,
                0,
                0
        );
        assertEquals("", withCode.pinnedProfileOrBlank());
    }

    private static List<AgentFlow.Node> nodes(String... ids) {
        List<AgentFlow.Node> items = new ArrayList<>();
        for (String id : ids) {
            items.add(new AgentFlow.Node(
                    id,
                    id,
                    "",
                    new ArrayList<ToolKind>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    0,
                    0
            ));
        }
        return items;
    }

    private static List<String> ids(List<AgentFlow.Node> nodes) {
        List<String> items = new ArrayList<>();
        for (AgentFlow.Node node : nodes) {
            items.add(node.id());
        }
        return items;
    }
}
