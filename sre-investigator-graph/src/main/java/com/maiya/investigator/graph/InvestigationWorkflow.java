package com.maiya.investigator.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.maiya.investigator.graph.route.InvestigationRouteResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InvestigationWorkflow {
    private final CompiledGraph graph;
    private final InvestigationRouteResolver routes;

    public InvestigationWorkflow(CompiledGraph graph, InvestigationRouteResolver routes) {
        this.graph = graph;
        this.routes = routes;
    }

    public InvestigationResult investigate(UUID projectId, String question, String environment) {
        return investigate(projectId, question, environment, null, null, null);
    }

    public InvestigationResult investigate(
            UUID projectId,
            String question,
            String environment,
            String provider,
            String model
    ) {
        return investigate(projectId, question, environment, provider, model, null);
    }

    public InvestigationResult investigate(
            UUID projectId,
            String question,
            String environment,
            String provider,
            String model,
            String requestedRoute
    ) {
        return investigate(projectId, question, environment, provider, model, requestedRoute, "", null, null);
    }

    public InvestigationResult investigate(
            UUID projectId,
            String question,
            String environment,
            String provider,
            String model,
            String requestedRoute,
            String memoryPrompt
    ) {
        return investigate(projectId, question, environment, provider, model, requestedRoute, memoryPrompt, null, null);
    }

    public InvestigationResult investigate(
            UUID projectId,
            String question,
            String environment,
            String provider,
            String model,
            String requestedRoute,
            String memoryPrompt,
            String mcpProfile,
            UUID repositoryId
    ) {
        return investigate(projectId, question, environment, provider, model, requestedRoute, memoryPrompt, mcpProfile, repositoryId, null, null, null);
    }

    public InvestigationResult investigate(
            UUID projectId,
            String question,
            String environment,
            String provider,
            String model,
            String requestedRoute,
            String memoryPrompt,
            String mcpProfile,
            UUID repositoryId,
            String agentSlug
    ) {
        return investigate(projectId, question, environment, provider, model, requestedRoute, memoryPrompt, mcpProfile, repositoryId, agentSlug, null, null);
    }

    public InvestigationResult investigate(
            UUID projectId,
            String question,
            String environment,
            String provider,
            String model,
            String requestedRoute,
            String memoryPrompt,
            String mcpProfile,
            UUID repositoryId,
            String agentSlug,
            String skillSlug,
            String ruleSlug
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", projectId.toString());
        input.put("question", question);
        input.put("memoryPrompt", memoryPrompt == null ? "" : memoryPrompt);
        input.put("environment", environment);
        input.put("provider", provider == null ? "" : provider);
        input.put("model", model == null ? "" : model);
        input.put("requestedRoute", requestedRoute == null ? "" : requestedRoute);
        input.put("mcpProfile", mcpProfile == null ? "" : mcpProfile);
        input.put("repositoryId", repositoryId == null ? "" : repositoryId.toString());
        input.put("agentSlug", agentSlug == null ? "" : agentSlug);
        input.put("skillSlug", skillSlug == null ? "" : skillSlug);
        input.put("ruleSlug", ruleSlug == null ? "" : ruleSlug);
        OverAllState state = graph.invoke(input).orElseThrow(() -> new IllegalStateException("排查工作流未返回状态"));
        return new InvestigationResult(
                state.value("route", routes.fallbackId()),
                state.value("report", ""),
                exposedState(state.data())
        );
    }

    // investigationState 是循环 Graph 内部的累积器（证据/假设/工具账本），图跑完即失效，图外无人读取，
    // 且用的是 question() 式访问器、无 getter，Jackson 会以「无属性 Bean」报错。对外只暴露可序列化的结果 state，
    // 避免 SSE / REST 下发 RunOutcome 时序列化失败。
    static Map<String, Object> exposedState(Map<String, Object> data) {
        Map<String, Object> exposed = new LinkedHashMap<>(data);
        exposed.remove("investigationState");
        return exposed;
    }

    public record InvestigationResult(
            String route,
            String report,
            Map<String, Object> state
    ) {
    }
}
