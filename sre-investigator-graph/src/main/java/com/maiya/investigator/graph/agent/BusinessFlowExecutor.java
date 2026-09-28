package com.maiya.investigator.graph.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.graph.AgentProperties;
import com.maiya.investigator.graph.AgentThoughts;
import com.maiya.investigator.graph.InvestigateCommand;
import com.maiya.investigator.graph.InvestigationAgent;
import com.maiya.investigator.graph.InvestigationAgent.AgentResult;
import com.maiya.investigator.graph.skill.SkillLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 子 Agent 业务流程图的分阶执行：系统编排仍走到 investigate 节点，
 * 本类按拓扑序逐步打开该步 MCP / Skill，把本步结论交给下一步。
 */
@Component
public class BusinessFlowExecutor {
    private static final Logger log = LoggerFactory.getLogger(BusinessFlowExecutor.class);

    private final InvestigationAgent investigation;
    private final SkillLoader skills;
    private final AgentProperties properties;

    public BusinessFlowExecutor(
            InvestigationAgent investigation,
            SkillLoader skills,
            AgentProperties properties
    ) {
        this.investigation = investigation;
        this.skills = skills;
        this.properties = properties == null ? new AgentProperties() : properties;
    }

    public AgentResult investigate(OverAllState state, AgentDefinition definition, String fallbackRoute) {
        if (definition == null || !definition.hasFlow()) {
            return singleRound(state, fallbackRoute, properties.getMaxToolCalls());
        }
        List<AgentFlow.Node> ordered;
        try {
            ordered = definition.flow().topologicalOrder();
        } catch (IllegalArgumentException exception) {
            AgentThoughts.emit("phase", "业务流程图存在环，已降级为单轮排查");
            log.warn("业务流程图存在环，降级单轮排查 slug={}", definition.slug());
            return singleRound(state, fallbackRoute, properties.getMaxToolCalls());
        }
        if (ordered.isEmpty()) {
            return singleRound(state, fallbackRoute, properties.getMaxToolCalls());
        }
        UUID projectId = parseProjectId(state.value("projectId", ""));
        int max = properties.getMaxToolCalls();
        int used = 0;
        boolean exhausted = false;
        boolean modelUsed = false;
        int availableTools = 0;
        StringBuilder report = new StringBuilder();
        List<String> prior = new ArrayList<>();
        String question = state.value("question", "");
        for (int i = 0; i < ordered.size(); i++) {
            int remaining = max - used;
            if (remaining <= 0) {
                AgentThoughts.emit("phase", "工具预算已用尽，后续业务步骤未执行");
                exhausted = true;
                break;
            }
            AgentFlow.Node node = ordered.get(i);
            String title = node.title().isBlank() ? "步骤 " + (i + 1) : node.title();
            AgentThoughts.emit("phase", "正在执行业务步骤「" + title + "」（" + (i + 1) + "/" + ordered.size() + "）");
            log.info("业务步骤开始 slug={} step={} id={} title={}", definition.slug(), i + 1, node.id(), title);
            AgentResult step = runNode(state, fallbackRoute, projectId, node, question, prior, remaining);
            modelUsed = modelUsed || step.modelUsed();
            used += Math.max(0, step.usedToolCalls());
            availableTools = Math.max(availableTools, step.availableTools());
            exhausted = exhausted || step.budgetExhausted();
            String content = step.content() == null ? "" : step.content();
            if (report.length() > 0) {
                report.append("\n\n");
            }
            report.append("## ").append(title).append('\n').append(content);
            prior.add(title + "：\n" + content);
            if (!step.modelUsed()) {
                AgentThoughts.emit("phase", "步骤「" + title + "」未跑完模型，后续业务步骤已停止");
                break;
            }
        }
        return new AgentResult(modelUsed, report.toString(), availableTools, used, exhausted, max);
    }

    private AgentResult runNode(
            OverAllState state,
            String fallbackRoute,
            UUID projectId,
            AgentFlow.Node node,
            String question,
            List<String> prior,
            int toolCallLimit
    ) {
        List<String> stepSkills = pinSkills(projectId, node.skillSlugs());
        return callInvestigate(
                state,
                fallbackRoute,
                composeQuestion(question, node, prior),
                AgentToolFilter.stageProfiles(strings(state, "mysqlProfiles"), node, ToolKind.MYSQL),
                AgentToolFilter.stageProfiles(strings(state, "redisProfiles"), node, ToolKind.REDIS),
                state.value("codeBound", false) && node.usesKind(ToolKind.CODE),
                stepSkills,
                AgentToolFilter.stageProfiles(strings(state, "logsProfiles"), node, ToolKind.LOGS),
                AgentToolFilter.stageProfiles(strings(state, "mseProfiles"), node, ToolKind.MSE),
                AgentToolFilter.stageProfiles(strings(state, "k8sProfiles"), node, ToolKind.K8S),
                AgentToolFilter.stageProfiles(strings(state, "starrocksProfiles"), node, ToolKind.STARROCKS),
                AgentToolFilter.stageProfiles(strings(state, "kafkaProfiles"), node, ToolKind.KAFKA),
                AgentToolFilter.stageProfiles(strings(state, "rocketmqProfiles"), node, ToolKind.ROCKETMQ),
                AgentToolFilter.stageProfiles(strings(state, "mongodbProfiles"), node, ToolKind.MONGODB),
                AgentToolFilter.stageProfiles(strings(state, "armsProfiles"), node, ToolKind.ARMS),
                AgentToolFilter.stageProfiles(strings(state, "loadBalancerProfiles"), node, ToolKind.LOAD_BALANCER),
                AgentToolFilter.stageProfiles(strings(state, "cdnProfiles"), node, ToolKind.CDN),
                node.pinnedProfileOrBlank(),
                toolCallLimit,
                ""
        );
    }

    /** 循环 Graph 用：跑一轮默认排查，extraContext 为此前累积的证据/账本，追加进 memoryPrompt。 */
    public AgentResult singleRound(OverAllState state, String fallbackRoute, int toolCallLimit, String extraContext) {
        return singleRound(state, fallbackRoute, toolCallLimit, extraContext, null);
    }

    /**
     * 循环 Graph 用（软门控扩种类）：override 非空时用环境可用全集覆盖各类 profile，
     * 让后续轮次够得到首轮被方向收窄掉的工具。
     */
    public AgentResult singleRound(
            OverAllState state,
            String fallbackRoute,
            int toolCallLimit,
            String extraContext,
            java.util.Map<ToolKind, List<String>> override
    ) {
        return callInvestigate(
                state,
                fallbackRoute,
                state.value("question", ""),
                widen(state, "mysqlProfiles", ToolKind.MYSQL, override),
                widen(state, "redisProfiles", ToolKind.REDIS, override),
                state.value("codeBound", false),
                strings(state, "skills"),
                widen(state, "logsProfiles", ToolKind.LOGS, override),
                widen(state, "mseProfiles", ToolKind.MSE, override),
                widen(state, "k8sProfiles", ToolKind.K8S, override),
                widen(state, "starrocksProfiles", ToolKind.STARROCKS, override),
                widen(state, "kafkaProfiles", ToolKind.KAFKA, override),
                widen(state, "rocketmqProfiles", ToolKind.ROCKETMQ, override),
                widen(state, "mongodbProfiles", ToolKind.MONGODB, override),
                widen(state, "armsProfiles", ToolKind.ARMS, override),
                widen(state, "loadBalancerProfiles", ToolKind.LOAD_BALANCER, override),
                widen(state, "cdnProfiles", ToolKind.CDN, override),
                state.value("mcpProfile", ""),
                toolCallLimit,
                extraContext
        );
    }

    private static List<String> widen(
            OverAllState state,
            String key,
            ToolKind kind,
            java.util.Map<ToolKind, List<String>> override
    ) {
        if (override != null && override.containsKey(kind)) {
            List<String> value = override.get(kind);
            if (value != null && !value.isEmpty()) {
                return new ArrayList<>(value);
            }
        }
        return strings(state, key);
    }

    // 单一实现：flow 分支的兜底与循环 Graph 的默认轮次都走同一个 singleRound，避免重复分步逻辑
    private AgentResult singleRound(OverAllState state, String fallbackRoute, int toolCallLimit) {
        return singleRound(state, fallbackRoute, toolCallLimit, "", null);
    }

    private AgentResult callInvestigate(
            OverAllState state,
            String fallbackRoute,
            String question,
            List<String> mysqlProfiles,
            List<String> redisProfiles,
            boolean codeBound,
            List<String> stepSkills,
            List<String> logsProfiles,
            List<String> mseProfiles,
            List<String> k8sProfiles,
            List<String> starrocksProfiles,
            List<String> kafkaProfiles,
            List<String> rocketmqProfiles,
            List<String> mongodbProfiles,
            List<String> armsProfiles,
            List<String> loadBalancerProfiles,
            List<String> cdnProfiles,
            String pinnedMcpProfile,
            int toolCallLimit,
            String extraContext
    ) {
        String route = state.value("route", fallbackRoute);
        if (route == null || route.isBlank()) {
            route = fallbackRoute;
        }
        String memoryPrompt = state.value("memoryPrompt", "");
        if (extraContext != null && !extraContext.isBlank()) {
            memoryPrompt = (memoryPrompt == null ? "" : memoryPrompt)
                    + "\n\n## 本轮之前已累积的进展\n" + extraContext.trim();
        }
        InvestigateCommand command = InvestigateCommand.builder()
                .question(question)
                .route(route)
                .environment(state.value("environment", "test"))
                .codeBound(codeBound)
                .skills(stepSkills)
                .rules(strings(state, "rules"))
                .provider(state.value("provider", ""))
                .model(state.value("model", ""))
                .projectId(state.value("projectId", ""))
                .toolCatalog(strings(state, "toolCatalog"))
                .memoryPrompt(memoryPrompt)
                .profile(ToolKind.MYSQL, mysqlProfiles)
                .profile(ToolKind.REDIS, redisProfiles)
                .profile(ToolKind.LOGS, logsProfiles)
                .profile(ToolKind.MSE, mseProfiles)
                .profile(ToolKind.K8S, k8sProfiles)
                .profile(ToolKind.STARROCKS, starrocksProfiles)
                .profile(ToolKind.KAFKA, kafkaProfiles)
                .profile(ToolKind.ROCKETMQ, rocketmqProfiles)
                .profile(ToolKind.MONGODB, mongodbProfiles)
                .profile(ToolKind.ARMS, armsProfiles)
                .profile(ToolKind.LOAD_BALANCER, loadBalancerProfiles)
                .profile(ToolKind.CDN, cdnProfiles)
                .pinnedMcpProfile(pinnedMcpProfile)
                .repositoryId(parseProjectId(state.value("repositoryId", "")))
                .agentSystemPrompt(state.value("agentSystemPrompt", ""))
                .toolCallLimit(toolCallLimit)
                .build();
        return investigation.investigate(command);
    }

    private List<String> pinSkills(UUID projectId, List<String> slugs) {
        List<String> items = new ArrayList<>();
        if (slugs == null) {
            return items;
        }
        for (String slug : slugs) {
            items.addAll(skills.compactBySlug(projectId, slug));
        }
        return items;
    }

    static String composeQuestion(String question, AgentFlow.Node node, List<String> prior) {
        String title = node == null || node.title().isBlank() ? "未命名步骤" : node.title();
        String instruction = node == null || node.instruction().isBlank()
                ? "按本步标题完成只读排查，写清证据。"
                : node.instruction();
        String previous = "（无）";
        if (prior != null && !prior.isEmpty()) {
            previous = String.join("\n\n", prior);
        }
        return """
                用户问题：
                %s

                当前步骤：%s
                本步目标：
                %s

                此前步骤结论：
                %s

                只完成本步，不要提前做后续步骤。本步结论必须写清证据来源。
                """.formatted(question == null ? "" : question, title, instruction, previous);
    }

    private static List<String> strings(OverAllState state, String key) {
        List<String> copy = new ArrayList<>();
        Object raw = state.value(key, new ArrayList<String>());
        if (!(raw instanceof List<?> list)) {
            return copy;
        }
        for (Object item : list) {
            if (item instanceof String text) {
                String value = text.trim();
                if (!value.isBlank() && !copy.contains(value)) {
                    copy.add(value);
                }
            }
        }
        return copy;
    }

    private static UUID parseProjectId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
