package com.maiya.investigator.graph.route;

import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.graph.agent.AgentDefinition;
import com.maiya.investigator.graph.intent.QuestionIntentHeuristics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 普通对话排查按方向白名单收窄工具种类；点名 MCP / 子 Agent 放行。
 * 问句只加种类，不减。纯代码理解问题只读代码，不挂数据类 MCP。
 */
public final class ToolKindGate {
    private static final Pattern K8S = Pattern.compile("(?i)k8s|kubernetes|pod|crashloop|oomkilled|imagepull");
    private static final Pattern LOGS = Pattern.compile("(?i)trace[-_\\s]?id|日志|sls|logstore");
    private static final Pattern ORDER = Pattern.compile("(?i)订单|支付|充值|回调|退款|到账");
    private static final Pattern STARROCKS = Pattern.compile("(?i)starrocks|数仓|olap");
    private static final Pattern MONGO = Pattern.compile("(?i)mongo|mongodb|集合");
    private static final Pattern KAFKA = Pattern.compile("(?i)kafka");
    private static final Pattern ROCKETMQ = Pattern.compile("(?i)rocketmq|火箭mq");
    private static final Pattern ARMS = Pattern.compile("(?i)\\barms\\b|\\bapm\\b|链路追踪");
    private static final Pattern LOAD_BALANCER = Pattern.compile("(?i)alb|clb|slb|负载均衡|listener|监听器");
    private static final Pattern CDN = Pattern.compile("(?i)\\bcdn\\b|静态资源|边缘节点|回源|源站");
    // 本次新增：代码理解意图信号，用于把纯读代码问题收窄到 CODE
    private static final Pattern CODE_UNDERSTANDING = Pattern.compile(
            "(?i)怎么实现|如何实现|实现原理|原理是|为什么这么|为什么要这么|讲讲|讲一下|讲解|解释一下|说明一下|"
                    + "怎么用|如何使用|作用是|有什么用|逻辑是|流程是|调用链|调用关系|在哪里定义|定义在哪|"
                    + "这个类|这个方法|这个函数|这个字段|这个枚举|这个接口|这段代码|代码里|梳理一下|理一下|"
                    + "是什么意思|做什么用|干什么用|读一下代码|看下代码|看一下代码|帮我理解");

    private ToolKindGate() {
    }

    /**
     * 判断是否为纯代码理解问题：命中代码理解信号，且不带运维/数据故障信号、无数据源关键词。
     * 命中则本轮只读代码，不挂任何数据类 MCP。
     */
    public static boolean codeUnderstanding(String question) {
        String text = question == null ? "" : question;
        if (!CODE_UNDERSTANDING.matcher(text).find()) {
            return false;
        }
        if (QuestionIntentHeuristics.hasOperationalSignal(text)) {
            return false;
        }
        List<ToolKind> dataKinds = new ArrayList<>();
        addQuestionKinds(dataKinds, text);
        return dataKinds.isEmpty();
    }

    public static boolean passthrough(boolean namedMcp, AgentDefinition definition) {
        return namedMcp || (definition != null && (definition.specialized() || definition.hasFlow()));
    }

    public static List<ToolKind> allow(
            InvestigationRoute route,
            String question,
            List<ToolKind> boundKinds,
            boolean passthrough
    ) {
        List<ToolKind> bound = copy(boundKinds);
        if (passthrough) {
            return bound;
        }
        // 本次新增：纯代码理解问题只读代码，不挂数据类 MCP
        if (bound.contains(ToolKind.CODE) && codeUnderstanding(question)) {
            List<ToolKind> onlyCode = new ArrayList<>();
            onlyCode.add(ToolKind.CODE);
            return onlyCode;
        }
        List<ToolKind> wanted = new ArrayList<>();
        if (route != null) {
            for (ToolKind kind : route.toolKinds()) {
                add(wanted, kind);
            }
        }
        addQuestionKinds(wanted, question);
        List<ToolKind> allowed = new ArrayList<>();
        for (ToolKind kind : wanted) {
            if (bound.contains(kind)) {
                add(allowed, kind);
            }
        }
        if (!allowed.isEmpty()) {
            return allowed;
        }
        // 本次修改：兜底优先给代码仓，只有没有代码仓时才退回 MySQL，不再无脑挂数据库
        if (bound.contains(ToolKind.CODE)) {
            add(allowed, ToolKind.CODE);
        }
        if (allowed.isEmpty() && bound.contains(ToolKind.MYSQL)) {
            add(allowed, ToolKind.MYSQL);
        }
        return allowed;
    }

    static void addQuestionKinds(List<ToolKind> wanted, String question) {
        String text = question == null ? "" : question;
        if (K8S.matcher(text).find()) {
            add(wanted, ToolKind.K8S);
        }
        if (LOGS.matcher(text).find()) {
            add(wanted, ToolKind.LOGS);
        }
        if (ORDER.matcher(text).find()) {
            add(wanted, ToolKind.MYSQL);
            add(wanted, ToolKind.REDIS);
        }
        if (STARROCKS.matcher(text).find()) {
            add(wanted, ToolKind.STARROCKS);
        }
        if (MONGO.matcher(text).find()) {
            add(wanted, ToolKind.MONGODB);
        }
        if (KAFKA.matcher(text).find()) {
            add(wanted, ToolKind.KAFKA);
        }
        if (ROCKETMQ.matcher(text).find()) {
            add(wanted, ToolKind.ROCKETMQ);
        }
        if (ARMS.matcher(text).find()) {
            add(wanted, ToolKind.ARMS);
        }
        if (LOAD_BALANCER.matcher(text).find()) {
            add(wanted, ToolKind.LOAD_BALANCER);
        }
        if (CDN.matcher(text).find()) {
            add(wanted, ToolKind.CDN);
        }
    }

    public static ToolKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ToolKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<ToolKind> copy(List<ToolKind> raw) {
        List<ToolKind> copy = new ArrayList<>();
        if (raw == null) {
            return copy;
        }
        for (ToolKind kind : raw) {
            add(copy, kind);
        }
        return copy;
    }

    private static void add(List<ToolKind> items, ToolKind kind) {
        if (kind != null && !items.contains(kind)) {
            items.add(kind);
        }
    }
}
