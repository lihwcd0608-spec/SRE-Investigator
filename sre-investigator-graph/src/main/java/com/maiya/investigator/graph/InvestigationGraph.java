package com.maiya.investigator.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolCatalog;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.graph.agent.AgentCatalog;
import com.maiya.investigator.graph.agent.AgentDefinition;
import com.maiya.investigator.graph.agent.AgentToolFilter;
import com.maiya.investigator.graph.agent.BusinessFlowExecutor;
import com.maiya.investigator.graph.route.InvestigationRouteCatalog;
import com.maiya.investigator.graph.route.InvestigationRouteResolver;
import com.maiya.investigator.graph.route.RouteDecision;
import com.maiya.investigator.graph.report.ReportMarkdown;
import com.maiya.investigator.graph.report.StructuredReport;
import com.maiya.investigator.graph.route.ToolKindGate;
import com.maiya.investigator.graph.skill.SkillLoader;
import com.maiya.investigator.graph.state.InvestigationState;
import com.maiya.investigator.mcp.MentionParser;
import com.maiya.investigator.mcp.NamedMcpFocus;
import com.maiya.investigator.mcp.ReadonlyCodeTool;
import com.maiya.investigator.mcp.config.RepositoryWorkspaceProperties;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InvestigationGraph {
    private static final Logger log = LoggerFactory.getLogger(InvestigationGraph.class);
    public static final String ROUTE = "route";
    public static final String LOAD_CONTEXT = "load_context";
    public static final String INVESTIGATE = "investigate";
    public static final String SYNTHESIZE = "synthesize";

    @Bean
    CompiledGraph compiledInvestigationGraph(
            InvestigationAgent agent,
            ControlPlaneStore controlPlane,
            SkillLoader skills,
            InvestigationRouteResolver routes,
            RepositoryWorkspaceProperties repositoryProperties,
            BusinessFlowExecutor flowExecutor,
            AgentProperties agentProperties
    ) throws GraphStateException {
        String fallbackRoute = routes.fallbackId();
        int maxIterations = agentProperties.getMaxIterations();
        StateGraph graph = new StateGraph(initialState());
        graph.addNode(ROUTE, state -> {
            String agentSlug = state.value("agentSlug", "");
            UUID projectId = parseRepositoryId(state.value("projectId", ""));
            AgentDefinition definition = AgentCatalog.resolve(controlPlane, projectId, agentSlug);
            if (definition != null && definition.specialized()) {
                AgentThoughts.emit("phase", "已选用子 Agent " + definition.name() + "，跳过排查方向分类");
                log.info("图节点 route=agent slug={} question={}", definition.slug(), LogText.preview(state.value("question", "")));
                return CompletableFuture.completedFuture(Map.of(
                        "route", fallbackRoute,
                        "routeSource", "AGENT",
                        "agentSystemPrompt", definition.systemPrompt() == null ? "" : definition.systemPrompt(),
                        "phase", ROUTE
                ));
            }
            String mcpProfile = state.value("mcpProfile", "");
            if (mcpProfile != null && !mcpProfile.isBlank()) {
                AgentThoughts.emit("phase", "已点名 MCP " + mcpProfile + "，跳过排查方向分类");
                log.info("图节点 route=named-mcp profile={} question={}", mcpProfile, LogText.preview(state.value("question", "")));
                return CompletableFuture.completedFuture(Map.of(
                        "route", fallbackRoute,
                        "routeSource", "NAMED_MCP",
                        "phase", ROUTE
                ));
            }
            String requested = state.value("requestedRoute", "");
            String routeQuestion = state.value("question", "");
            // 本次新增：纯代码理解问题跳过模型分类，直接用兜底方向，本轮只读代码
            if (InvestigationRouteCatalog.isAuto(requested) && ToolKindGate.codeUnderstanding(routeQuestion)) {
                AgentThoughts.emit("phase", "识别为代码理解问题，跳过排查方向分类，仅阅读代码");
                log.info("图节点 route=code-understanding question={}", LogText.preview(routeQuestion));
                return CompletableFuture.completedFuture(Map.of(
                        "route", fallbackRoute,
                        "routeSource", "CODE",
                        "phase", ROUTE
                ));
            }
            if (InvestigationRouteCatalog.isAuto(requested)) {
                AgentThoughts.emit("phase", "正在用模型判断排查方向");
            }
            RouteDecision decision = routes.resolve(
                    requested,
                    state.value("question", ""),
                    state.value("provider", ""),
                    state.value("model", "")
            );
            AgentThoughts.emit("phase", decision.thought());
            log.info(
                    "图节点 route={} source={} question={}",
                    decision.id(),
                    decision.source(),
                    LogText.preview(state.value("question", ""))
            );
            return CompletableFuture.completedFuture(Map.of(
                    "route", decision.id(),
                    "routeSource", decision.source().name(),
                    "phase", ROUTE
            ));
        });
        graph.addNode(LOAD_CONTEXT, state -> {
            AgentThoughts.emit("phase", "正在装载项目、环境与已启用的只读工具");
            UUID projectId = UUID.fromString(state.value("projectId", ""));
            Environment environment = Environment.from(state.value("environment", "test"));
            String question = state.value("question", "");
            String mcpProfile = state.value("mcpProfile", "");
            UUID repositoryId = parseRepositoryId(state.value("repositoryId", ""));
            List<ToolBinding> bindings = controlPlane.bindings(projectId).stream()
                    .filter(ToolBinding::enabled)
                    .filter(binding -> binding.environment() == environment)
                    .toList();
            List<String> mysqlProfiles = profilesFor(bindings, ToolKind.MYSQL);
            List<String> redisProfiles = profilesFor(bindings, ToolKind.REDIS);
            List<String> logsProfiles = profilesFor(bindings, ToolKind.LOGS);
            List<String> mseProfiles = profilesFor(bindings, ToolKind.MSE);
            List<String> k8sProfiles = profilesFor(bindings, ToolKind.K8S);
            List<String> starrocksProfiles = profilesFor(bindings, ToolKind.STARROCKS);
            List<String> kafkaProfiles = profilesFor(bindings, ToolKind.KAFKA);
            List<String> rocketmqProfiles = profilesFor(bindings, ToolKind.ROCKETMQ);
            List<String> mongodbProfiles = profilesFor(bindings, ToolKind.MONGODB);
            List<String> armsProfiles = profilesFor(bindings, ToolKind.ARMS);
            List<String> loadBalancerProfiles = profilesFor(bindings, ToolKind.LOAD_BALANCER);
            List<String> cdnProfiles = profilesFor(bindings, ToolKind.CDN);
            NamedMcpFocus.AppliedMcpFocus applied = NamedMcpFocus.apply(
                    bindings,
                    question,
                    mcpProfile,
                    mysqlProfiles,
                    redisProfiles,
                    logsProfiles,
                    mseProfiles,
                    k8sProfiles,
                    starrocksProfiles,
                    kafkaProfiles,
                    rocketmqProfiles,
                    mongodbProfiles,
                    armsProfiles,
                    loadBalancerProfiles,
                    cdnProfiles
            );
            mysqlProfiles = applied.mysqlProfiles();
            redisProfiles = applied.redisProfiles();
            logsProfiles = applied.logsProfiles();
            mseProfiles = applied.mseProfiles();
            k8sProfiles = applied.k8sProfiles();
            starrocksProfiles = applied.starrocksProfiles();
            kafkaProfiles = applied.kafkaProfiles();
            rocketmqProfiles = applied.rocketmqProfiles();
            mongodbProfiles = applied.mongodbProfiles();
            armsProfiles = applied.armsProfiles();
            loadBalancerProfiles = applied.loadBalancerProfiles();
            cdnProfiles = applied.cdnProfiles();
            AgentDefinition definition = AgentCatalog.resolve(controlPlane, projectId, state.value("agentSlug", ""));
            String route = state.value("route", fallbackRoute);
            // 软门控：保存"环境可用全集"（仅按 NamedMcpFocus + 子 Agent 过滤，不按方向收窄），
            // 供循环 Graph 在首轮未结论时按需扩种类，避免开局锁死后中途够不到日志/数仓等工具。
            Map<ToolKind, List<String>> widenProfiles = new EnumMap<>(ToolKind.class);
            putWiden(widenProfiles, ToolKind.MYSQL, AgentToolFilter.profiles(mysqlProfiles, definition, ToolKind.MYSQL));
            putWiden(widenProfiles, ToolKind.REDIS, AgentToolFilter.profiles(redisProfiles, definition, ToolKind.REDIS));
            putWiden(widenProfiles, ToolKind.LOGS, AgentToolFilter.profiles(logsProfiles, definition, ToolKind.LOGS));
            putWiden(widenProfiles, ToolKind.MSE, AgentToolFilter.profiles(mseProfiles, definition, ToolKind.MSE));
            putWiden(widenProfiles, ToolKind.K8S, AgentToolFilter.profiles(k8sProfiles, definition, ToolKind.K8S));
            putWiden(widenProfiles, ToolKind.STARROCKS, AgentToolFilter.profiles(starrocksProfiles, definition, ToolKind.STARROCKS));
            putWiden(widenProfiles, ToolKind.KAFKA, AgentToolFilter.profiles(kafkaProfiles, definition, ToolKind.KAFKA));
            putWiden(widenProfiles, ToolKind.ROCKETMQ, AgentToolFilter.profiles(rocketmqProfiles, definition, ToolKind.ROCKETMQ));
            putWiden(widenProfiles, ToolKind.MONGODB, AgentToolFilter.profiles(mongodbProfiles, definition, ToolKind.MONGODB));
            putWiden(widenProfiles, ToolKind.ARMS, AgentToolFilter.profiles(armsProfiles, definition, ToolKind.ARMS));
            putWiden(widenProfiles, ToolKind.LOAD_BALANCER, AgentToolFilter.profiles(loadBalancerProfiles, definition, ToolKind.LOAD_BALANCER));
            putWiden(widenProfiles, ToolKind.CDN, AgentToolFilter.profiles(cdnProfiles, definition, ToolKind.CDN));
            List<ToolKind> allowedKinds = ToolKindGate.allow(
                    routes.catalog().find(route).orElse(null),
                    question,
                    boundKinds(bindings),
                    ToolKindGate.passthrough(applied.focus().isPresent(), definition)
            );
            mysqlProfiles = keepProfiles(mysqlProfiles, ToolKind.MYSQL, allowedKinds);
            redisProfiles = keepProfiles(redisProfiles, ToolKind.REDIS, allowedKinds);
            logsProfiles = keepProfiles(logsProfiles, ToolKind.LOGS, allowedKinds);
            mseProfiles = keepProfiles(mseProfiles, ToolKind.MSE, allowedKinds);
            k8sProfiles = keepProfiles(k8sProfiles, ToolKind.K8S, allowedKinds);
            starrocksProfiles = keepProfiles(starrocksProfiles, ToolKind.STARROCKS, allowedKinds);
            kafkaProfiles = keepProfiles(kafkaProfiles, ToolKind.KAFKA, allowedKinds);
            rocketmqProfiles = keepProfiles(rocketmqProfiles, ToolKind.ROCKETMQ, allowedKinds);
            mongodbProfiles = keepProfiles(mongodbProfiles, ToolKind.MONGODB, allowedKinds);
            armsProfiles = keepProfiles(armsProfiles, ToolKind.ARMS, allowedKinds);
            loadBalancerProfiles = keepProfiles(loadBalancerProfiles, ToolKind.LOAD_BALANCER, allowedKinds);
            cdnProfiles = keepProfiles(cdnProfiles, ToolKind.CDN, allowedKinds);
            mysqlProfiles = AgentToolFilter.profiles(mysqlProfiles, definition, ToolKind.MYSQL);
            redisProfiles = AgentToolFilter.profiles(redisProfiles, definition, ToolKind.REDIS);
            logsProfiles = AgentToolFilter.profiles(logsProfiles, definition, ToolKind.LOGS);
            mseProfiles = AgentToolFilter.profiles(mseProfiles, definition, ToolKind.MSE);
            k8sProfiles = AgentToolFilter.profiles(k8sProfiles, definition, ToolKind.K8S);
            starrocksProfiles = AgentToolFilter.profiles(starrocksProfiles, definition, ToolKind.STARROCKS);
            kafkaProfiles = AgentToolFilter.profiles(kafkaProfiles, definition, ToolKind.KAFKA);
            rocketmqProfiles = AgentToolFilter.profiles(rocketmqProfiles, definition, ToolKind.ROCKETMQ);
            mongodbProfiles = AgentToolFilter.profiles(mongodbProfiles, definition, ToolKind.MONGODB);
            armsProfiles = AgentToolFilter.profiles(armsProfiles, definition, ToolKind.ARMS);
            loadBalancerProfiles = AgentToolFilter.profiles(loadBalancerProfiles, definition, ToolKind.LOAD_BALANCER);
            cdnProfiles = AgentToolFilter.profiles(cdnProfiles, definition, ToolKind.CDN);
            int repoCount = resolveRepoCount(
                    controlPlane.repositories(projectId),
                    repositoryId,
                    repositoryProperties.getDefaultName(),
                    question,
                    applied.focus().isPresent()
            );
            boolean codeBound = repoCount > 0
                    || bindings.stream().anyMatch(binding -> binding.toolKind() == ToolKind.CODE);
            if (applied.focus().isPresent() && MentionParser.extract(question).isEmpty()) {
                codeBound = false;
                repoCount = 0;
            }
            if (definition != null && !definition.allows(ToolKind.CODE)) {
                codeBound = false;
                repoCount = 0;
            }
            if (!allowedKinds.contains(ToolKind.CODE)) {
                codeBound = false;
                repoCount = 0;
            }
            String skillSlug = state.value("skillSlug", "");
            String ruleSlug = state.value("ruleSlug", "");
            List<String> skillContext;
            if (applied.focus().isPresent()) {
                skillContext = new java.util.ArrayList<>();
            } else if (definition != null && definition.hasFlow()) {
                skillContext = new java.util.ArrayList<>();
            } else if (definition != null && definition.specialized()) {
                skillContext = pinSkills(skills, projectId, definition.skillSlugs());
            } else {
                skillContext = resolveSkills(skills, projectId, skillSlug, route);
            }
            List<String> ruleContext;
            if (applied.focus().isPresent() || (definition != null && definition.specialized())) {
                ruleContext = new java.util.ArrayList<>();
            } else {
                ruleContext = skills.mergePinnedRule(skills.compactRules(projectId), projectId, ruleSlug);
            }
            List<ToolBinding> catalogBindings = applied.focus().isEmpty()
                    ? bindings
                    : bindings.stream()
                            .filter(binding -> binding.toolKind() == applied.focus().get().toolKind()
                                    && binding.profile().equals(applied.focus().get().profile()))
                            .toList();
            catalogBindings = AgentToolFilter.bindings(catalogBindings, definition);
            catalogBindings = keepBindings(catalogBindings, allowedKinds);
            List<String> toolCatalog = ToolCatalog.lines(catalogBindings, controlPlane.assets(projectId));
            log.info(
                    "图节点 load_context project={} env={} route={} mysql={} redis={} sls={} mse={} arms={} lb={} cdn={} k8s={} starrocks={} kafka={} rocketmq={} mongodb={} codeBound={} repos={} skills={} rules={}",
                    projectId,
                    environment,
                    route,
                    mysqlProfiles,
                    redisProfiles,
                    logsProfiles,
                    mseProfiles,
                    armsProfiles,
                    loadBalancerProfiles,
                    cdnProfiles,
                    k8sProfiles,
                    starrocksProfiles,
                    kafkaProfiles,
                    rocketmqProfiles,
                    mongodbProfiles,
                    codeBound,
                    repoCount,
                    skillContext.size(),
                    ruleContext.size()
            );
            if (applied.focus().isPresent()) {
                NamedMcpFocus focus = applied.focus().get();
                AgentThoughts.emit(
                        "phase",
                        "用户点名 MCP " + focus.profile() + "（" + focus.kindLabel() + "），本轮仅启用对应只读工具"
                );
            }
            if (definition != null && definition.hasFlow()) {
                AgentThoughts.emit("phase", "子 Agent " + definition.name() + " 将按业务流程图分步执行");
            } else if (definition != null && definition.specialized()) {
                AgentThoughts.emit("phase", "子 Agent " + definition.name() + " 已按声明收窄工具");
            }
            AgentThoughts.emit("phase", contextThought(mysqlProfiles, redisProfiles, logsProfiles, mseProfiles, k8sProfiles, starrocksProfiles, kafkaProfiles, rocketmqProfiles, mongodbProfiles, armsProfiles, loadBalancerProfiles, cdnProfiles, codeBound, repoCount));
            if (!skillContext.isEmpty()) {
                AgentThoughts.emit("phase", "已装载 " + skillContext.size() + " 条 Skill");
            }
            if (!ruleContext.isEmpty()) {
                AgentThoughts.emit("phase", "已装载 " + ruleContext.size() + " 条 Rule");
            }
            return CompletableFuture.completedFuture(Map.ofEntries(
                    Map.entry("contextLoaded", true),
                    Map.entry("mysqlProfiles", mysqlProfiles),
                    Map.entry("redisProfiles", redisProfiles),
                    Map.entry("logsProfiles", logsProfiles),
                    Map.entry("mseProfiles", mseProfiles),
                    Map.entry("k8sProfiles", k8sProfiles),
                    Map.entry("starrocksProfiles", starrocksProfiles),
                    Map.entry("kafkaProfiles", kafkaProfiles),
                    Map.entry("rocketmqProfiles", rocketmqProfiles),
                    Map.entry("mongodbProfiles", mongodbProfiles),
                    Map.entry("armsProfiles", armsProfiles),
                    Map.entry("loadBalancerProfiles", loadBalancerProfiles),
                    Map.entry("cdnProfiles", cdnProfiles),
                    Map.entry("codeBound", codeBound),
                    Map.entry("widenProfilesByKind", widenProfiles),
                    Map.entry("skills", skillContext),
                    Map.entry("rules", ruleContext),
                    Map.entry("toolCatalog", toolCatalog),
                    Map.entry("agentSystemPrompt", definition != null && definition.specialized() ? definition.systemPrompt() : state.value("agentSystemPrompt", "")),
                    Map.entry("phase", LOAD_CONTEXT)
            ));
        });
        // INVESTIGATE 从"单次黑盒调用"改为"循环 Graph 的一轮 plan+act+observe"：
        // 每轮把此前累积的证据/账本回喂模型，由下方条件边 decide 决定继续或收尾。
        graph.addNode(INVESTIGATE, state -> {
            UUID projectId = parseRepositoryId(state.value("projectId", ""));
            AgentDefinition definition = AgentCatalog.resolve(controlPlane, projectId, state.value("agentSlug", ""));
            InvestigationState investigation = state.value("investigationState", (InvestigationState) null);
            if (investigation == null) {
                investigation = new InvestigationState(
                        state.value("question", ""),
                        state.value("route", fallbackRoute),
                        agentProperties.getMaxToolCalls()
                );
            }
            int iteration = investigation.nextIteration();
            InvestigationAgent.AgentResult result;
            if (definition != null && definition.hasFlow()) {
                // 子 Agent 业务流程图内部已做分步，视为单轮，不进入外层循环
                result = flowExecutor.investigate(state, definition, fallbackRoute);
            } else {
                String extraContext = iteration > 1 ? investigation.toContext() : "";
                int perRoundBudget = Math.max(1, investigation.remainingBudget());
                @SuppressWarnings("unchecked")
                Map<ToolKind, List<String>> widen = state.value("widenProfilesByKind", (Map<ToolKind, List<String>>) null);
                if (iteration > 1) {
                    AgentThoughts.emit("phase", "证据不足，进入第 " + iteration + " 轮取证，放开本环境全部只读工具");
                    // 软门控：首轮未结论时扩到环境可用全集，让模型够得到之前被方向收窄掉的工具
                    result = flowExecutor.singleRound(state, fallbackRoute, perRoundBudget, extraContext, widen);
                } else {
                    result = flowExecutor.singleRound(state, fallbackRoute, perRoundBudget, extraContext);
                }
            }
            investigation.recordRound(iteration, result.content());
            investigation.addBudgetUsed(result.usedToolCalls());
            return CompletableFuture.completedFuture(Map.ofEntries(
                    Map.entry("investigationState", investigation),
                    Map.entry("iteration", iteration),
                    Map.entry("investigation", result.content() == null ? "" : result.content()),
                    Map.entry("modelUsed", result.modelUsed()),
                    Map.entry("availableTools", result.availableTools()),
                    Map.entry("usedToolCalls", investigation.budgetUsed()),
                    Map.entry("budgetExhausted", result.budgetExhausted() || investigation.budgetExhausted()),
                    Map.entry("maxToolCalls", agentProperties.getMaxToolCalls()),
                    Map.entry("phase", INVESTIGATE)
            ));
        });
        graph.addNode(SYNTHESIZE, state -> {
            String report = agent.synthesize(
                    state.value("question", ""),
                    state.value("route", fallbackRoute),
                    new InvestigationAgent.AgentResult(
                            state.value("modelUsed", false),
                            state.value("investigation", ""),
                            state.value("availableTools", 0),
                            state.value("usedToolCalls", 0),
                            state.value("budgetExhausted", false),
                            state.value("maxToolCalls", 0)
                    )
            );
            return CompletableFuture.completedFuture(Map.of(
                    "report", report,
                    "reportStructured", structuredReport(report),
                    "phase", SYNTHESIZE
            ));
        });
        graph.addEdge(StateGraph.START, ROUTE);
        graph.addEdge(ROUTE, LOAD_CONTEXT);
        graph.addEdge(LOAD_CONTEXT, INVESTIGATE);
        graph.addConditionalEdges(
                INVESTIGATE,
                AsyncEdgeAction.edge_async(state -> decideNext(state, controlPlane, maxIterations)),
                Map.of("continue", INVESTIGATE, "done", SYNTHESIZE)
        );
        graph.addEdge(SYNTHESIZE, StateGraph.END);
        return graph.compile();
    }

    /**
     * 循环 Graph 的条件边：证据充分 / 预算耗尽 / 达到迭代上限 / 模型已给出四段结论 / 点名 MCP
     * 或子 Agent 单轮场景 → done（去 synthesize）；否则 continue（回 investigate 再取证）。
     */
    static String decideNext(OverAllState state, ControlPlaneStore controlPlane, int maxIterations) {
        boolean modelUsed = state.value("modelUsed", false);
        if (!modelUsed) {
            return "done";
        }
        boolean budgetExhausted = state.value("budgetExhausted", false);
        if (budgetExhausted) {
            return "done";
        }
        int iteration = state.value("iteration", 1);
        if (iteration >= maxIterations) {
            return "done";
        }
        String routeSource = state.value("routeSource", "");
        String mcpProfile = state.value("mcpProfile", "");
        if ("NAMED_MCP".equals(routeSource) || (mcpProfile != null && !mcpProfile.isBlank())) {
            return "done";
        }
        UUID projectId = parseRepositoryId(state.value("projectId", ""));
        AgentDefinition definition = AgentCatalog.resolve(controlPlane, projectId, state.value("agentSlug", ""));
        if (definition != null && (definition.specialized() || definition.hasFlow())) {
            return "done";
        }
        String content = state.value("investigation", "");
        if (content != null && content.contains("已确认的证据")) {
            return "done";
        }
        return "continue";
    }

    private static List<String> resolveSkills(SkillLoader skills, UUID projectId, String skillSlug, String route) {
        if (skillSlug != null && !skillSlug.isBlank()) {
            List<String> pinned = skills.compactBySlug(projectId, skillSlug);
            if (!pinned.isEmpty()) {
                AgentThoughts.emit("phase", "已指定 Skill " + skillSlug);
                return pinned;
            }
            AgentThoughts.emit("phase", "未找到指定 Skill " + skillSlug + "，已按当前场景装载 Skill");
        }
        return new java.util.ArrayList<>(skills.compactContext(projectId, route));
    }

    private static List<String> pinSkills(SkillLoader skills, UUID projectId, List<String> slugs) {
        List<String> items = new java.util.ArrayList<>();
        if (slugs == null) {
            return items;
        }
        for (String slug : slugs) {
            items.addAll(skills.compactBySlug(projectId, slug));
        }
        return items;
    }

    private static String contextThought(
            List<String> mysqlProfiles,
            List<String> redisProfiles,
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
            boolean codeBound,
            int repoCount
    ) {
        List<String> parts = new java.util.ArrayList<>();
        if (!mysqlProfiles.isEmpty()) {
            parts.add("MySQL " + String.join("/", mysqlProfiles));
        }
        if (!redisProfiles.isEmpty()) {
            parts.add("Redis " + String.join("/", redisProfiles));
        }
        if (!logsProfiles.isEmpty()) {
            parts.add("SLS " + String.join("/", logsProfiles));
        }
        if (!mseProfiles.isEmpty()) {
            parts.add("MSE " + String.join("/", mseProfiles));
        }
        if (!armsProfiles.isEmpty()) {
            parts.add("ARMS " + String.join("/", armsProfiles));
        }
        if (!loadBalancerProfiles.isEmpty()) {
            parts.add("负载均衡 " + String.join("/", loadBalancerProfiles));
        }
        if (!cdnProfiles.isEmpty()) {
            parts.add("CDN " + String.join("/", cdnProfiles));
        }
        if (!k8sProfiles.isEmpty()) {
            parts.add("K8s " + String.join("/", k8sProfiles));
        }
        if (!starrocksProfiles.isEmpty()) {
            parts.add("StarRocks " + String.join("/", starrocksProfiles));
        }
        if (!kafkaProfiles.isEmpty()) {
            parts.add("Kafka " + String.join("/", kafkaProfiles));
        }
        if (!rocketmqProfiles.isEmpty()) {
            parts.add("RocketMQ " + String.join("/", rocketmqProfiles));
        }
        if (!mongodbProfiles.isEmpty()) {
            parts.add("MongoDB " + String.join("/", mongodbProfiles));
        }
        if (codeBound) {
            parts.add("代码仓 " + repoCount + " 个");
        }
        if (parts.isEmpty()) {
            return "当前环境没有启用的只读工具，将仅用模型和已装载上下文排查";
        }
        return "已装载 " + String.join("，", parts);
    }

    private static OverAllState initialState() {
        OverAllStateBuilder builder = OverAllStateBuilder.builder();
        for (String key : List.of(
                "question",
                "memoryPrompt",
                "projectId",
                "environment",
                "provider",
                "model",
                "requestedRoute",
                "route",
                "routeSource",
                "contextLoaded",
                "mysqlProfiles",
                "redisProfiles",
                "logsProfiles",
                "mseProfiles",
                "k8sProfiles",
                "starrocksProfiles",
                "kafkaProfiles",
                "rocketmqProfiles",
                "mongodbProfiles",
                "armsProfiles",
                "loadBalancerProfiles",
                "cdnProfiles",
                "mcpProfile",
                "agentSlug",
                "skillSlug",
                "ruleSlug",
                "agentSystemPrompt",
                "repositoryId",
                "codeBound",
                "widenProfilesByKind",
                "skills",
                "rules",
                "toolCatalog",
                "investigation",
                "investigationState",
                "iteration",
                "modelUsed",
                "availableTools",
                "usedToolCalls",
                "budgetExhausted",
                "maxToolCalls",
                "report",
                "reportStructured",
                "phase"
        )) {
            builder.withKeyStrategy(key, new ReplaceStrategy());
        }
        return builder.build();
    }

    private static List<ToolKind> boundKinds(List<ToolBinding> bindings) {
        List<ToolKind> kinds = new ArrayList<>();
        kinds.add(ToolKind.CODE);
        if (bindings == null) {
            return kinds;
        }
        for (ToolBinding binding : bindings) {
            if (binding != null && binding.toolKind() != null && !kinds.contains(binding.toolKind())) {
                kinds.add(binding.toolKind());
            }
        }
        return kinds;
    }

    private static List<String> keepProfiles(List<String> profiles, ToolKind kind, List<ToolKind> allowed) {
        List<String> copy = new ArrayList<>();
        if (allowed == null || !allowed.contains(kind) || profiles == null) {
            return copy;
        }
        for (String profile : profiles) {
            if (profile != null && !profile.isBlank() && !copy.contains(profile)) {
                copy.add(profile);
            }
        }
        return copy;
    }

    private static List<ToolBinding> keepBindings(List<ToolBinding> bindings, List<ToolKind> allowed) {
        List<ToolBinding> copy = new ArrayList<>();
        if (bindings == null) {
            return copy;
        }
        for (ToolBinding binding : bindings) {
            if (binding != null && allowed != null && allowed.contains(binding.toolKind())) {
                copy.add(binding);
            }
        }
        return copy;
    }

    /** 把最终四段 Markdown 报告解析成结构化字段，随 state 上浮给前端做证据面板。 */
    private static Map<String, Object> structuredReport(String report) {
        StructuredReport structured = ReportMarkdown.parse(report);
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("phenomenon", structured.phenomenon());
        map.put("confirmedEvidence", structured.confirmedEvidence());
        map.put("ruledOut", structured.ruledOut());
        map.put("hypotheses", structured.hypotheses());
        map.put("nextSteps", structured.nextSteps());
        return map;
    }

    private static void putWiden(Map<ToolKind, List<String>> target, ToolKind kind, List<String> profiles) {
        if (kind != null && profiles != null && !profiles.isEmpty()) {
            target.put(kind, new ArrayList<>(profiles));
        }
    }

    private static List<String> profilesFor(List<ToolBinding> bindings, ToolKind toolKind) {
        return bindings.stream()
                .filter(binding -> binding.toolKind() == toolKind)
                .map(ToolBinding::profile)
                .distinct()
                .toList();
    }

    private static UUID parseRepositoryId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int resolveRepoCount(
            List<com.maiya.investigator.domain.control.RepositoryConfig> repositories,
            UUID repositoryId,
            String defaultRepositoryName,
            String question,
            boolean mcpFocused
    ) {
        if (mcpFocused && MentionParser.extract(question).isEmpty()) {
            return 0;
        }
        if (!MentionParser.extract(question).isEmpty()) {
            return repositories.size();
        }
        if (repositoryId != null) {
            return repositories.stream().anyMatch(item -> item.id().equals(repositoryId)) ? 1 : 0;
        }
        long matched = repositories.stream()
                .filter(item -> ReadonlyCodeTool.repositoryNameMatches(item.name(), defaultRepositoryName))
                .count();
        return matched > 0 ? (int) matched : 0;
    }
}
