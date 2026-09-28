package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.aliyun.MseRpcClient;
import com.maiya.investigator.mcp.config.MseEndpoint;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyMseTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyMseTool.class);
    static final int MAX_RESULT_CHARS = 16_000;
    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final MseRpcClient client;

    public ReadonlyMseTool(BindingConnectionLookup bindings, AuditTrail auditTrail, MseRpcClient client) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> listGateways(UUID projectId, String environment, String profile) {
        return invoke(projectId, environment, profile, "ListGateway", Map.of("PageNumber", "1", "PageSize", "20"));
    }

    public Map<String, Object> listRoutes(UUID projectId, String environment, String profile, String keyword) {
        MseEndpoint endpoint = require(projectId, environment, profile);
        Map<String, String> params = pageParams();
        params.put("GatewayUniqueId", endpoint.requireGatewayId(null));
        if (keyword != null && !keyword.isBlank()) {
            params.put("Filter.Name", keyword.trim());
        }
        return invoke(endpoint, profile, "ListGatewayRoute", params);
    }

    public Map<String, Object> getRoute(UUID projectId, String environment, String profile, String routeId) {
        requireText(routeId, "routeId");
        MseEndpoint endpoint = require(projectId, environment, profile);
        return invoke(endpoint, profile, "GetGatewayRouteDetail", Map.of(
                "GatewayUniqueId", endpoint.requireGatewayId(null),
                "RouteId", routeId.trim()
        ));
    }

    public Map<String, Object> listServices(UUID projectId, String environment, String profile, String keyword) {
        MseEndpoint endpoint = require(projectId, environment, profile);
        Map<String, String> params = pageParams();
        params.put("GatewayUniqueId", endpoint.requireGatewayId(null));
        if (keyword != null && !keyword.isBlank()) {
            params.put("Filter.Name", keyword.trim());
        }
        return invoke(endpoint, profile, "ListGatewayService", params);
    }

    public Map<String, Object> getService(UUID projectId, String environment, String profile, String serviceId) {
        requireText(serviceId, "serviceId");
        MseEndpoint endpoint = require(projectId, environment, profile);
        return invoke(endpoint, profile, "GetGatewayServiceDetail", Map.of(
                "GatewayUniqueId", endpoint.requireGatewayId(null),
                "ServiceId", serviceId.trim()
        ));
    }

    public Map<String, Object> listNacosServices(UUID projectId, String environment, String profile, String keyword, String namespaceId) {
        MseEndpoint endpoint = require(projectId, environment, profile);
        Map<String, String> params = pageParams();
        params.put("InstanceId", endpoint.requireNacosInstanceId(null));
        put(params, "NamespaceId", namespaceId);
        put(params, "ServiceName", keyword);
        return invoke(endpoint, profile, "ListAnsServices", params);
    }

    public Map<String, Object> listNacosInstances(
            UUID projectId,
            String environment,
            String profile,
            String serviceName,
            String namespaceId,
            String clusterName
    ) {
        requireText(serviceName, "serviceName");
        MseEndpoint endpoint = require(projectId, environment, profile);
        Map<String, String> params = pageParams();
        params.put("InstanceId", endpoint.requireNacosInstanceId(null));
        params.put("ServiceName", serviceName.trim());
        put(params, "NamespaceId", namespaceId);
        put(params, "ClusterName", clusterName);
        return invoke(endpoint, profile, "ListAnsInstances", params);
    }

    public Map<String, Object> getNacosConfig(
            UUID projectId,
            String environment,
            String profile,
            String dataId,
            String group,
            String namespaceId
    ) {
        requireText(dataId, "dataId");
        MseEndpoint endpoint = require(projectId, environment, profile);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("InstanceId", endpoint.requireNacosInstanceId(null));
        params.put("DataId", dataId.trim());
        params.put("Group", group == null || group.isBlank() ? "DEFAULT_GROUP" : group.trim());
        put(params, "NamespaceId", namespaceId);
        return invoke(endpoint, profile, "GetNacosConfig", params);
    }

    public Map<String, Object> listGovernance(
            UUID projectId,
            String environment,
            String profile,
            String appName,
            String ruleType,
            String namespace
    ) {
        requireText(appName, "appName");
        String type = ruleType == null ? "flow" : ruleType.trim().toLowerCase(Locale.ROOT);
        String ns = namespace == null || namespace.isBlank() ? "default" : namespace.trim();
        Map<String, String> params = pageParams();
        params.put("AppName", appName.trim());
        params.put("Namespace", ns);
        String action = switch (type) {
            case "flow", "limit", "throttle" -> "ListFlowRules";
            case "circuit", "breaker", "degrade" -> "ListCircuitBreakerRules";
            case "isolation", "fallback" -> "ListIsolationRules";
            case "gray", "lane", "swimming" -> "QueryAllSwimmingLane";
            default -> throw new IllegalArgumentException("不支持的治理规则类型：" + ruleType + "。可用：flow / circuit / isolation / gray");
        };
        return invoke(projectId, environment, profile, action, params);
    }

    private Map<String, Object> invoke(
            UUID projectId,
            String environment,
            String profile,
            String action,
            Map<String, String> params
    ) {
        return invoke(require(projectId, environment, profile), profile, action, params);
    }

    private Map<String, Object> invoke(MseEndpoint endpoint, String profile, String action, Map<String, String> params) {
        log.info("MSE 只读 {} profile={} region={}", action, profile, endpoint.regionId());
        auditTrail.record("mse." + action, profile == null ? "" : profile, Map.of(
                "regionId", endpoint.regionId(),
                "action", action
        ));
        return clip(client.call(endpoint, action, params));
    }

    private MseEndpoint require(UUID projectId, String environment, String profile) {
        if (projectId == null) {
            throw new IllegalStateException("MSE 只使用 MCP 页保存的连接，当前没有项目上下文");
        }
        return bindings.mse(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 MCP MSE：" + (profile == null ? "" : profile)
                        + "。请到 MCP 页保存 MSE Binding 并点「测试连接」。"
        ));
    }

    private static Map<String, String> pageParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("PageNumber", "1");
        params.put("PageSize", "20");
        params.put("PageNo", "1");
        params.put("PageIndex", "1");
        return params;
    }

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value.trim());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    static Map<String, Object> clip(Map<String, Object> result) {
        String json = String.valueOf(result);
        if (json.length() <= MAX_RESULT_CHARS) {
            return result;
        }
        Map<String, Object> clipped = new LinkedHashMap<>();
        clipped.put("ok", result.getOrDefault("ok", true));
        clipped.put("action", result.get("action"));
        clipped.put("truncated", true);
        clipped.put("note", "结果过长，已截断进 prompt");
        Object data = result.get("Data");
        if (data == null) {
            data = result.get("data");
        }
        clipped.put("data", data == null ? clipText(json, MAX_RESULT_CHARS) : data);
        return clipped;
    }

    private static String clipText(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
