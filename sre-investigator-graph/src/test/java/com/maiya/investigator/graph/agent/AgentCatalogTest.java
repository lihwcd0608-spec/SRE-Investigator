package com.maiya.investigator.graph.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogSource;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.ToolKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentCatalogTest {
    @Test
    void resolvesBuiltinK8sInspect() {
        AgentDefinition agent = AgentCatalog.resolve(null, null, "k8s-inspect");
        assertNotNull(agent);
        assertEquals("k8s-inspect", agent.slug());
        assertTrue(agent.specialized());
        assertTrue(agent.allows(ToolKind.K8S));
        assertFalse(agent.allows(ToolKind.LOGS));
        assertFalse(agent.allows(ToolKind.MYSQL));
        assertFalse(agent.starters().isEmpty());
        assertTrue(agent.systemPrompt().contains("Kubernetes"));
    }

    @Test
    void resolvesBuiltinIncidentFlowPlaybook() {
        AgentDefinition agent = AgentCatalog.resolve(null, null, "sre-incident-flow");
        assertNotNull(agent);
        assertEquals("sre-incident-flow", agent.slug());
        assertEquals("标准排查", agent.name());
        assertTrue(agent.specialized());
        assertTrue(agent.hasFlow());
        assertEquals(5, agent.flow().nodes().size());
        assertEquals("n1", agent.flow().topologicalOrder().get(0).id());
        assertEquals("n5", agent.flow().topologicalOrder().get(4).id());
        assertTrue(agent.allows(ToolKind.CODE));
        assertTrue(agent.allows(ToolKind.MSE));
        assertTrue(agent.allows(ToolKind.K8S));
        assertTrue(agent.allows(ToolKind.LOGS));
        assertTrue(agent.allows(ToolKind.MYSQL));
        assertTrue(agent.allows(ToolKind.REDIS));
        assertFalse(agent.allows(ToolKind.KAFKA));
        assertTrue(agent.flow().nodes().get(0).skillSlugs().contains("http-5xx"));
        assertTrue(agent.systemPrompt().contains("业务流程图"));
    }

    @Test
    void defaultSreInvestigateIsNotSpecialized() {
        AgentDefinition agent = AgentCatalog.resolve(null, null, "sre-investigate");
        assertNotNull(agent);
        assertFalse(agent.specialized());
        assertTrue(agent.allows(ToolKind.LOGS));
        assertTrue(agent.allows(ToolKind.K8S));
    }

    @Test
    void unknownSlugReturnsNull() {
        assertNull(AgentCatalog.resolve(null, null, "no-such-agent"));
        assertNull(AgentCatalog.resolve(null, null, " "));
        assertNull(AgentCatalog.resolve(null, null, "mysql-readonly"));
        assertNull(AgentCatalog.resolve(null, null, "log-triage"));
        assertNull(AgentCatalog.resolve(null, null, "code-locator"));
    }

    @Test
    void projectAssetOverridesBuiltin() {
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        CatalogAsset asset = CatalogAsset.create(
                projectId,
                CatalogKind.AGENT,
                CatalogSource.CUSTOM,
                "k8s-inspect",
                "项目定制 K8s",
                now
        ).withDetails(
                CatalogSource.CUSTOM,
                "k8s-inspect",
                "项目定制 K8s",
                null,
                "local",
                "agent-k8s-inspect",
                null,
                null,
                null,
                null,
                null,
                "{\"systemPrompt\":\"只用本项目提示词\",\"allowedToolKinds\":[\"K8S\"],\"skillSlugs\":[],\"starters\":[\"看事件\"]}",
                "override",
                "mysql",
                now
        );
        List<CatalogAsset> assets = new ArrayList<>();
        assets.add(asset);
        ControlPlaneStore store = mock(ControlPlaneStore.class);
        when(store.assets(projectId)).thenReturn(assets);

        AgentDefinition agent = AgentCatalog.resolve(store, projectId, "k8s-inspect");
        assertNotNull(agent);
        assertEquals("项目定制 K8s", agent.name());
        assertEquals("只用本项目提示词", agent.systemPrompt());
        assertEquals(1, agent.starters().size());
        assertEquals("看事件", agent.starters().get(0));
    }

    @Test
    void parseFlowJsonAndUnionKindsFromNodes() {
        AgentDefinition agent = AgentCatalog.parse(
                "order-path",
                "订单链路",
                """
                {
                  "systemPrompt": "只读查订单",
                  "allowedToolKinds": [],
                  "skillSlugs": [],
                  "starters": [],
                  "flow": {
                    "nodes": [
                      {
                        "id": "n1",
                        "title": "核订单",
                        "instruction": "按 orderId 查订单状态",
                        "toolKinds": [],
                        "skillSlugs": ["order-payment"],
                        "mcps": [{"toolKind": "MYSQL", "profile": "zg_mysql_test"}],
                        "x": 40,
                        "y": 40
                      },
                      {
                        "id": "n2",
                        "title": "看日志",
                        "instruction": "",
                        "toolKinds": ["LOGS"],
                        "skillSlugs": [],
                        "mcps": [{"toolKind": "LOGS", "profile": "prod-logs"}],
                        "x": 240,
                        "y": 40
                      }
                    ],
                    "edges": [{"from": "n1", "to": "n2"}]
                  }
                }
                """
        );
        assertTrue(agent.hasFlow());
        assertEquals(2, agent.flow().nodes().size());
        assertEquals("n1", agent.flow().topologicalOrder().get(0).id());
        assertEquals("zg_mysql_test", agent.flow().nodes().get(0).singleProfileOrBlank());
        assertTrue(agent.allows(ToolKind.MYSQL));
        assertTrue(agent.allows(ToolKind.LOGS));
        assertFalse(agent.allows(ToolKind.K8S));
        assertEquals("按 orderId 查订单状态", agent.flow().nodes().get(0).instruction());
    }
}
