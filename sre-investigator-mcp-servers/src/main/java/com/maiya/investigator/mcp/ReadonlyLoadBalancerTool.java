package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.aliyun.AliyunCloudClient;
import com.maiya.investigator.mcp.config.LoadBalancerEndpoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyLoadBalancerTool {
    private static final int MAX_RESULT_CHARS = 24_000;

    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final AliyunCloudClient client;

    public ReadonlyLoadBalancerTool(
            BindingConnectionLookup bindings,
            AuditTrail auditTrail,
            AliyunCloudClient client
    ) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> list(UUID projectId, String environment, String profile) {
        LoadBalancerEndpoint endpoint = require(projectId, environment, profile);
        auditTrail.record("loadbalancer.list", profile, Map.of(
                "regionId", endpoint.regionId(),
                "serviceType", endpoint.serviceType(),
                "scopedInstances", endpoint.instanceIds().size()
        ));
        if (endpoint.instanceIds().isEmpty()) {
            return clip(listOne(endpoint, null));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (String instanceId : endpoint.instanceIds()) {
            results.add(listOne(endpoint, instanceId));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("serviceType", endpoint.serviceType());
        response.put("regionId", endpoint.regionId());
        response.put("count", results.size());
        response.put("results", results);
        return clip(response);
    }

    public Map<String, Object> health(
            UUID projectId,
            String environment,
            String profile,
            String instanceId,
            Integer listenerPort
    ) {
        LoadBalancerEndpoint endpoint = require(projectId, environment, profile);
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("负载均衡实例 ID 不能为空");
        }
        endpoint.requireAllowedInstance(instanceId);
        Map<String, String> params = new LinkedHashMap<>();
        if ("ALB".equals(endpoint.serviceType())) {
            params.put("LoadBalancerId", instanceId.trim());
        } else {
            params.put("LoadBalancerId", instanceId.trim());
            if (listenerPort != null && listenerPort > 0) {
                params.put("ListenerPort", String.valueOf(listenerPort));
            }
        }
        auditTrail.record("loadbalancer.health", profile, Map.of(
                "regionId", endpoint.regionId(),
                "serviceType", endpoint.serviceType(),
                "instanceId", instanceId
        ));
        Map<String, Object> result = "ALB".equals(endpoint.serviceType())
                ? client.alb(endpoint.regionId(), "GetListenerHealthStatus", params)
                : client.clb(endpoint.regionId(), "DescribeHealthStatus", params);
        return clip(result);
    }

    private Map<String, Object> listOne(LoadBalancerEndpoint endpoint, String instanceId) {
        Map<String, String> params = new LinkedHashMap<>();
        if ("ALB".equals(endpoint.serviceType())) {
            params.put("MaxResults", "20");
            if (instanceId != null) {
                params.put("LoadBalancerIds.1", instanceId);
            }
            return client.alb(endpoint.regionId(), "ListLoadBalancers", params);
        }
        params.put("PageNumber", "1");
        params.put("PageSize", "20");
        if (instanceId != null) {
            params.put("LoadBalancerId", instanceId);
        }
        return client.clb(endpoint.regionId(), "DescribeLoadBalancers", params);
    }

    private LoadBalancerEndpoint require(UUID projectId, String environment, String profile) {
        if (!client.configured()) {
            throw new IllegalStateException("负载均衡工具未配置全局阿里云 RAM 只读 AK");
        }
        return bindings.loadBalancer(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的负载均衡 Binding：" + (profile == null ? "" : profile)
        ));
    }

    private static Map<String, Object> clip(Map<String, Object> result) {
        if (String.valueOf(result).length() <= MAX_RESULT_CHARS) {
            return result;
        }
        Map<String, Object> clipped = new LinkedHashMap<>();
        clipped.put("ok", result.getOrDefault("ok", true));
        clipped.put("serviceType", result.get("serviceType"));
        clipped.put("truncated", true);
        clipped.put("note", "负载均衡结果过长，已截断；请配置或指定实例 ID");
        return clipped;
    }
}
