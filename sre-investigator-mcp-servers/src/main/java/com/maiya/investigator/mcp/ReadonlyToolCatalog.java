package com.maiya.investigator.mcp;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.List;
import java.util.Map;

/**
 * 只读工具面的单一事实来源：每个 {@link ToolKind} 对应哪些只读工具名。
 *
 * <p>这是 MCP 工具面重构的接缝——编排端的 capabilities() 与后续要独立暴露的
 * MCP server 都从这里取工具清单，避免在多处硬编码同一份 43 项名单导致漂移。
 * CODE / SKILL 不绑定具体 ToolKind profile，单独列出。
 */
public final class ReadonlyToolCatalog {

    private static final Map<ToolKind, List<String>> BY_KIND = Map.ofEntries(
            Map.entry(ToolKind.MYSQL, List.of("mysql_query_readonly")),
            Map.entry(ToolKind.REDIS, List.of("redis_get", "redis_inspect")),
            Map.entry(ToolKind.LOGS, List.of("sls_query_readonly", "sls_list_logstores")),
            Map.entry(ToolKind.MSE, List.of(
                    "mse_list_gateways", "mse_list_routes", "mse_get_route",
                    "mse_list_services", "mse_get_service", "mse_list_nacos_services",
                    "mse_list_nacos_instances", "mse_get_nacos_config", "mse_list_governance")),
            Map.entry(ToolKind.ARMS, List.of("arms_list_apps", "arms_search_traces", "arms_get_trace")),
            Map.entry(ToolKind.LOAD_BALANCER, List.of("lb_list", "lb_health")),
            Map.entry(ToolKind.CDN, List.of("cdn_list_domains", "cdn_domain_detail", "cdn_usage")),
            Map.entry(ToolKind.K8S, List.of(
                    "k8s_list_clusters", "k8s_list_namespaces", "k8s_list_nodes",
                    "k8s_list_pods", "k8s_get_pod", "k8s_list_events", "k8s_pod_logs")),
            Map.entry(ToolKind.STARROCKS, List.of("starrocks_query_readonly")),
            Map.entry(ToolKind.KAFKA, List.of("kafka_list_topics", "kafka_describe_topic", "kafka_peek_messages", "kafka_consumer_lag")),
            Map.entry(ToolKind.ROCKETMQ, List.of("rocketmq_list_topics", "rocketmq_peek_messages", "rocketmq_consumer_lag")),
            Map.entry(ToolKind.MONGODB, List.of("mongodb_list_databases", "mongodb_list_collections", "mongodb_query_readonly"))
    );

    /** 代码工具：不按环境 profile 收窄，只要有代码仓即可。 */
    public static final List<String> CODE_TOOLS = List.of("code_read", "code_search");
    /** Skill 工具：读取当前项目已装载 Skill 的附属文件。 */
    public static final List<String> SKILL_TOOLS = List.of("skill_read");

    private ReadonlyToolCatalog() {
    }

    public static List<String> forKind(ToolKind kind) {
        return BY_KIND.getOrDefault(kind, List.of());
    }

    /** 全部本地只读工具名（含 code / skill），供 capabilities 与 MCP server 暴露使用。 */
    public static List<String> allLocalToolNames() {
        List<String> names = new java.util.ArrayList<>();
        for (ToolKind kind : List.of(
                ToolKind.MYSQL, ToolKind.REDIS, ToolKind.LOGS, ToolKind.MSE,
                ToolKind.ARMS, ToolKind.LOAD_BALANCER, ToolKind.CDN, ToolKind.K8S,
                ToolKind.STARROCKS, ToolKind.KAFKA, ToolKind.ROCKETMQ, ToolKind.MONGODB)) {
            names.addAll(forKind(kind));
        }
        names.addAll(CODE_TOOLS);
        names.addAll(SKILL_TOOLS);
        return names;
    }
}
