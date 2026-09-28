package com.maiya.investigator.graph.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.graph.agent.AgentDefinition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolKindGateTest {
    @Test
    void http5xxWithoutK8sKeywordsDropsWarehouseAndQueue() {
        List<ToolKind> allowed = ToolKindGate.allow(
                http5xx(),
                "网关 5xx 超时",
                allBound(),
                false
        );
        assertTrue(allowed.contains(ToolKind.LOGS));
        assertTrue(allowed.contains(ToolKind.MSE));
        assertTrue(allowed.contains(ToolKind.ARMS));
        assertTrue(allowed.contains(ToolKind.LOAD_BALANCER));
        assertTrue(allowed.contains(ToolKind.MYSQL));
        assertTrue(allowed.contains(ToolKind.CODE));
        assertFalse(allowed.contains(ToolKind.CDN));
        assertFalse(allowed.contains(ToolKind.STARROCKS));
        assertFalse(allowed.contains(ToolKind.KAFKA));
        assertFalse(allowed.contains(ToolKind.ROCKETMQ));
        assertFalse(allowed.contains(ToolKind.MONGODB));
    }

    @Test
    void orderPaymentHasNoK8s() {
        List<ToolKind> allowed = ToolKindGate.allow(
                orderPayment(),
                "支付回调丢了",
                allBound(),
                false
        );
        assertTrue(allowed.contains(ToolKind.MYSQL));
        assertTrue(allowed.contains(ToolKind.REDIS));
        assertTrue(allowed.contains(ToolKind.CODE));
        assertFalse(allowed.contains(ToolKind.K8S));
        assertFalse(allowed.contains(ToolKind.LOGS));
    }

    @Test
    void questionCanAddK8sToHttpRoute() {
        List<ToolKind> allowed = ToolKindGate.allow(
                http5xx(),
                "接口 5xx 且 pod CrashLoop",
                allBound(),
                false
        );
        assertTrue(allowed.contains(ToolKind.K8S));
    }

    @Test
    void namedMcpDoesNotGate() {
        List<ToolKind> bound = new ArrayList<>();
        bound.add(ToolKind.MONGODB);
        bound.add(ToolKind.MYSQL);
        List<ToolKind> allowed = ToolKindGate.allow(http5xx(), "使用 zg_mongo mcp", bound, true);
        assertTrue(allowed.contains(ToolKind.MONGODB));
        assertTrue(allowed.contains(ToolKind.MYSQL));
    }

    @Test
    void specializedAgentPassthrough() {
        AgentDefinition agent = new AgentDefinition(
                "order-bot",
                "订单",
                "只查订单",
                List.of(ToolKind.MYSQL),
                List.of(),
                List.of()
        );
        assertTrue(ToolKindGate.passthrough(false, agent));
    }

    @Test
    void emptyIntersectionFallsBackToCodeNotMysql() {
        List<ToolKind> bound = new ArrayList<>();
        bound.add(ToolKind.CODE);
        bound.add(ToolKind.MYSQL);
        // 兜底优先给代码仓，不再无脑挂 MySQL
        List<ToolKind> allowed = ToolKindGate.allow(null, "随便看一眼", bound, false);
        assertTrue(allowed.contains(ToolKind.CODE));
        assertFalse(allowed.contains(ToolKind.MYSQL));
    }

    @Test
    void emptyIntersectionWithoutCodeStillFallsBackToMysql() {
        List<ToolKind> bound = new ArrayList<>();
        bound.add(ToolKind.MYSQL);
        // 没有代码仓时才退回 MySQL，避免完全无工具
        List<ToolKind> allowed = ToolKindGate.allow(null, "随便看一眼", bound, false);
        assertTrue(allowed.contains(ToolKind.MYSQL));
    }

    @Test
    void codeUnderstandingKeepsOnlyCode() {
        // 纯代码理解问题只读代码，不挂任何数据类 MCP
        List<ToolKind> allowed = ToolKindGate.allow(
                http5xx(),
                "这个方法怎么实现的，帮我讲讲调用链",
                allBound(),
                false
        );
        assertTrue(allowed.contains(ToolKind.CODE));
        assertFalse(allowed.contains(ToolKind.MYSQL));
        assertFalse(allowed.contains(ToolKind.LOGS));
        assertFalse(allowed.contains(ToolKind.MSE));
    }

    @Test
    void codeQuestionWithDataSignalKeepsData() {
        // 既问代码又带订单/接口等运维信号，不收窄，保留数据类工具
        List<ToolKind> allowed = ToolKindGate.allow(
                http5xx(),
                "订单支付接口是怎么实现的，为什么金额对不上",
                allBound(),
                false
        );
        assertTrue(allowed.contains(ToolKind.MYSQL));
        assertTrue(allowed.contains(ToolKind.CODE));
    }

    @Test
    void codeUnderstandingIgnoredWhenNoCodeRepo() {
        // 没有绑定代码仓时，代码理解判定不生效，走普通兜底
        List<ToolKind> bound = new ArrayList<>();
        bound.add(ToolKind.MYSQL);
        List<ToolKind> allowed = ToolKindGate.allow(null, "这个方法怎么实现的", bound, false);
        assertTrue(allowed.contains(ToolKind.MYSQL));
    }

    @Test
    void detectsCodeUnderstandingIntent() {
        assertTrue(ToolKindGate.codeUnderstanding("这个类是怎么实现的"));
        assertFalse(ToolKindGate.codeUnderstanding("订单支付回调为什么失败"));
        assertFalse(ToolKindGate.codeUnderstanding("mongo 集合的查询逻辑是怎么实现的"));
    }

    @Test
    void cdnKeywordsAddCdnKind() {
        List<ToolKind> allowed = ToolKindGate.allow(
                http5xx(),
                "静态资源回源 5xx",
                allBound(),
                false
        );
        assertTrue(allowed.contains(ToolKind.CDN));
        assertTrue(allowed.contains(ToolKind.ARMS));
    }

    private static InvestigationRoute http5xx() {
        return InvestigationRouteCatalog.BUILTIN.get(0);
    }

    private static InvestigationRoute orderPayment() {
        return InvestigationRouteCatalog.BUILTIN.get(1);
    }

    private static List<ToolKind> allBound() {
        List<ToolKind> kinds = new ArrayList<>();
        kinds.add(ToolKind.CODE);
        kinds.add(ToolKind.MYSQL);
        kinds.add(ToolKind.REDIS);
        kinds.add(ToolKind.LOGS);
        kinds.add(ToolKind.MSE);
        kinds.add(ToolKind.ARMS);
        kinds.add(ToolKind.LOAD_BALANCER);
        kinds.add(ToolKind.CDN);
        kinds.add(ToolKind.K8S);
        kinds.add(ToolKind.STARROCKS);
        kinds.add(ToolKind.KAFKA);
        kinds.add(ToolKind.ROCKETMQ);
        kinds.add(ToolKind.MONGODB);
        return kinds;
    }
}
