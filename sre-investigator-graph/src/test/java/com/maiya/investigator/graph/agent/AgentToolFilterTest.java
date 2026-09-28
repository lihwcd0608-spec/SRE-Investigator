package com.maiya.investigator.graph.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentToolFilterTest {
    @Test
    void specializedMysqlKeepsOnlyMysqlProfiles() {
        AgentDefinition mysql = mysqlAgent();
        List<String> logs = new ArrayList<>();
        logs.add("prod-logs");
        List<String> tables = new ArrayList<>();
        tables.add("order");

        assertTrue(AgentToolFilter.profiles(logs, mysql, ToolKind.LOGS).isEmpty());
        assertEquals(tables, AgentToolFilter.profiles(tables, mysql, ToolKind.MYSQL));
        assertTrue(AgentToolFilter.profiles(tables, mysql, ToolKind.K8S).isEmpty());
    }

    @Test
    void defaultSreInvestigateKeepsAllProfiles() {
        AgentDefinition sre = new AgentDefinition(
                AgentDefinition.DEFAULT_SLUG,
                "SRE 排障",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
        List<String> logs = new ArrayList<>();
        logs.add("prod-logs");
        assertFalse(sre.specialized());
        assertEquals(logs, AgentToolFilter.profiles(logs, sre, ToolKind.LOGS));
    }

    @Test
    void emptyKindsAllowNothingWhenSpecialized() {
        AgentDefinition profile = new AgentDefinition(
                "user-profile",
                "用户画像",
                "当前没有用户域查询工具",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
        List<String> mysql = new ArrayList<>();
        mysql.add("order");
        assertTrue(profile.specialized());
        assertTrue(AgentToolFilter.profiles(mysql, profile, ToolKind.MYSQL).isEmpty());
        assertFalse(profile.allows(ToolKind.MYSQL));
    }

    @Test
    void bindingsDropKindsTheAgentDidNotDeclare() {
        AgentDefinition mysql = mysqlAgent();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        List<ToolBinding> current = new ArrayList<>();
        current.add(ToolBinding.create(projectId, Environment.TEST, ToolKind.MYSQL, "order", now));
        current.add(ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "prod-logs", now));
        current.add(ToolBinding.create(projectId, Environment.TEST, ToolKind.K8S, "prod-k8s", now));

        List<ToolBinding> kept = AgentToolFilter.bindings(current, mysql);
        assertEquals(1, kept.size());
        assertEquals(ToolKind.MYSQL, kept.get(0).toolKind());
    }

    @Test
    void nullAgentLeavesBindingsAlone() {
        List<String> logs = new ArrayList<>();
        logs.add("prod-logs");
        assertEquals(logs, AgentToolFilter.profiles(logs, null, ToolKind.LOGS));
    }

    @Test
    void flowKindsCountEvenWhenCardKindsEmpty() {
        List<ToolKind> kinds = new ArrayList<>();
        kinds.add(ToolKind.MYSQL);
        List<AgentFlow.Node> nodes = new ArrayList<>();
        nodes.add(new AgentFlow.Node("n1", "查库", "", kinds, new ArrayList<>(), new ArrayList<>(), 0, 0));
        AgentDefinition agent = new AgentDefinition(
                "order-path",
                "订单",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new AgentFlow(nodes, new ArrayList<>())
        );
        assertTrue(agent.allows(ToolKind.MYSQL));
        assertFalse(agent.allows(ToolKind.LOGS));
        List<String> mysql = new ArrayList<>();
        mysql.add("order");
        assertEquals(mysql, AgentToolFilter.profiles(mysql, agent, ToolKind.MYSQL));
    }

    @Test
    void stageProfilesKeepNamedMcpOnly() {
        List<String> loaded = new ArrayList<>();
        loaded.add("zg_mysql_test");
        loaded.add("other");
        List<AgentFlow.Mcp> mcps = new ArrayList<>();
        mcps.add(new AgentFlow.Mcp(ToolKind.MYSQL, "zg_mysql_test"));
        AgentFlow.Node node = new AgentFlow.Node(
                "n1",
                "核订单",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                mcps,
                0,
                0
        );
        List<String> staged = AgentToolFilter.stageProfiles(loaded, node, ToolKind.MYSQL);
        assertEquals(1, staged.size());
        assertEquals("zg_mysql_test", staged.get(0));
        assertTrue(AgentToolFilter.stageProfiles(loaded, node, ToolKind.LOGS).isEmpty());
    }

    @Test
    void stageProfilesKeepAllWhenKindHasNoProfile() {
        List<String> loaded = new ArrayList<>();
        loaded.add("zg_mysql_test");
        loaded.add("other");
        List<ToolKind> kinds = new ArrayList<>();
        kinds.add(ToolKind.MYSQL);
        AgentFlow.Node node = new AgentFlow.Node(
                "n1",
                "查库",
                "",
                kinds,
                new ArrayList<>(),
                new ArrayList<>(),
                0,
                0
        );
        assertEquals(loaded, AgentToolFilter.stageProfiles(loaded, node, ToolKind.MYSQL));
    }

    private static AgentDefinition mysqlAgent() {
        List<ToolKind> kinds = new ArrayList<>();
        kinds.add(ToolKind.MYSQL);
        return new AgentDefinition("mysql-readonly", "MySQL 只读", "只用 MYSQL", kinds, new ArrayList<>(), new ArrayList<>());
    }
}
