package com.maiya.investigator.graph;

import java.util.List;
import java.util.Map;

final class ToolThought {
    private ToolThought() {
    }

    static String label(String name) {
        if (name == null) {
            return "工具";
        }
        return switch (name) {
            case "mysql_query_readonly" -> "查 MySQL";
            case "redis_get" -> "读 Redis";
            case "redis_inspect" -> "看 Redis";
            case "code_read" -> "读代码";
            case "code_search" -> "搜代码";
            case "skill_read" -> "读 Skill 文件";
            case "sls_query_readonly" -> "查 SLS";
            case "sls_list_logstores" -> "列 Logstore";
            case "mse_list_gateways" -> "列 MSE 网关";
            case "mse_list_routes" -> "列网关路由";
            case "mse_get_route" -> "看网关路由";
            case "mse_list_services" -> "列网关服务";
            case "mse_get_service" -> "看网关服务";
            case "mse_list_nacos_services" -> "列 Nacos 服务";
            case "mse_list_nacos_instances" -> "列 Nacos 实例";
            case "mse_get_nacos_config" -> "读 Nacos 配置";
            case "mse_list_governance" -> "查治理规则";
            case "k8s_list_clusters" -> "列集群";
            case "k8s_list_namespaces" -> "列命名空间";
            case "k8s_list_nodes" -> "列节点";
            case "k8s_list_pods" -> "列 Pod";
            case "k8s_get_pod" -> "看 Pod";
            case "k8s_list_events" -> "列事件";
            case "k8s_pod_logs" -> "读容器日志";
            case "starrocks_query_readonly" -> "查 StarRocks";
            case "kafka_list_topics" -> "列 Kafka topic";
            case "kafka_describe_topic" -> "看 Kafka topic";
            case "kafka_peek_messages" -> "偷看 Kafka";
            case "kafka_consumer_lag" -> "看 Kafka 积压";
            case "rocketmq_list_topics" -> "列 RocketMQ topic";
            case "rocketmq_peek_messages" -> "偷看 RocketMQ";
            case "rocketmq_consumer_lag" -> "看 RocketMQ 积压";
            case "mongodb_list_databases" -> "列 MongoDB 库";
            case "mongodb_list_collections" -> "列 MongoDB 集合";
            case "mongodb_query_readonly" -> "查 MongoDB";
            default -> name;
        };
    }

    static String start(String name, String detail) {
        String label = label(name);
        String clipped = AgentThoughts.clip(detail, 80);
        return clipped.isBlank() ? "正在" + label : "正在" + label + "：" + clipped;
    }

    static String done(String name, Object result) {
        return label(name) + "完成" + hint(result);
    }

    static String failed(String name, String error) {
        return label(name) + "失败：" + AgentThoughts.clip(error, 80);
    }

    static String hint(Object result) {
        if (result instanceof Map<?, ?> map) {
            if (Boolean.FALSE.equals(map.get("ok"))) {
                Object error = map.get("error");
                return "，失败：" + AgentThoughts.clip(error == null ? "" : String.valueOf(error), 80);
            }
            Object matches = map.get("matches");
            if (matches instanceof List<?> list) {
                return "，命中 " + list.size() + " 处";
            }
            if (map.containsKey("totalLines")) {
                return "，读到 " + map.get("startLine") + "-" + map.get("endLine") + " 行（共 " + map.get("totalLines") + " 行）";
            }
            if (map.containsKey("value")) {
                return map.get("value") == null ? "，key 不存在" : "，已读到值";
            }
            if (map.containsKey("type")) {
                return "，类型 " + map.get("type") + "，TTL " + map.get("ttl");
            }
            if (map.get("count") instanceof Number count) {
                return "，返回 " + count + " 条";
            }
        }
        if (result instanceof List<?> list) {
            return "，返回 " + list.size() + " 行";
        }
        if (result instanceof String text) {
            return "，返回 " + text.length() + " 字";
        }
        return "";
    }
}
