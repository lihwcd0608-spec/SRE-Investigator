package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.aliyun.AliyunCloudClient;
import com.maiya.investigator.mcp.config.ArmsEndpoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyArmsTool {
    private static final int MAX_LIMIT = 50;
    private static final int MAX_RESULT_CHARS = 24_000;

    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final AliyunCloudClient client;

    public ReadonlyArmsTool(
            BindingConnectionLookup bindings,
            AuditTrail auditTrail,
            AliyunCloudClient client
    ) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> listApps(UUID projectId, String environment, String profile) {
        ArmsEndpoint endpoint = require(projectId, environment, profile);
        auditTrail.record("arms.list.apps", profile, Map.of("regionId", endpoint.regionId()));
        if (!endpoint.applications().isEmpty()) {
            Map<String, Object> scoped = new LinkedHashMap<>();
            scoped.put("ok", true);
            scoped.put("regionId", endpoint.regionId());
            scoped.put("applications", new ArrayList<>(endpoint.applications()));
            scoped.put("count", endpoint.applications().size());
            scoped.put("note", "仅返回这个 Binding 明确开放的 ARMS 应用");
            return scoped;
        }
        return clip(client.arms(endpoint.regionId(), "ListTraceApps", Map.of("AppType", "TRACE")));
    }

    public Map<String, Object> searchTraces(
            UUID projectId,
            String environment,
            String profile,
            String application,
            String operation,
            String from,
            String to,
            Integer minDurationMs,
            Integer limit
    ) {
        ArmsEndpoint endpoint = require(projectId, environment, profile);
        endpoint.requireAllowedApplication(application);
        if (application == null || application.isBlank()) {
            throw new IllegalArgumentException("ARMS 调用链查询必须指定应用名称");
        }
        QueryTimeWindow.Range window = QueryTimeWindow.resolve(from, to);
        int capped = QueryTimeWindow.limit(limit, 20, MAX_LIMIT);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("StartTime", String.valueOf(window.fromEpochSec() * 1000));
        params.put("EndTime", String.valueOf(window.toEpochSec() * 1000));
        params.put("ServiceName", application.trim());
        params.put("PageNumber", "1");
        params.put("PageSize", String.valueOf(capped));
        params.put("Reverse", "true");
        if (operation != null && !operation.isBlank()) {
            params.put("OperationName", operation.trim());
        }
        if (minDurationMs != null && minDurationMs > 0) {
            params.put("MinDuration", String.valueOf(minDurationMs));
        }
        auditTrail.record("arms.search.traces", profile, Map.of(
                "regionId", endpoint.regionId(),
                "application", application,
                "from", window.fromEpochSec(),
                "to", window.toEpochSec(),
                "limit", capped
        ));
        return clip(client.arms(endpoint.regionId(), "SearchTraces", params));
    }

    public Map<String, Object> getTrace(
            UUID projectId,
            String environment,
            String profile,
            String application,
            String traceId,
            String from,
            String to
    ) {
        ArmsEndpoint endpoint = require(projectId, environment, profile);
        endpoint.requireAllowedApplication(application);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("TraceID", traceId.trim());
        params.put("PageNumber", "1");
        params.put("PageSize", "100");
        if (traceId.trim().length() != 30 || hasText(from) || hasText(to)) {
            QueryTimeWindow.Range window = QueryTimeWindow.resolve(from, to);
            params.put("StartTime", String.valueOf(window.fromEpochSec() * 1000));
            params.put("EndTime", String.valueOf(window.toEpochSec() * 1000));
        }
        auditTrail.record("arms.get.trace", profile, Map.of(
                "regionId", endpoint.regionId(),
                "application", application == null ? "" : application,
                "traceIdSuffix", suffix(traceId)
        ));
        return clip(client.arms(endpoint.regionId(), "GetTrace", params));
    }

    private ArmsEndpoint require(UUID projectId, String environment, String profile) {
        if (!client.configured()) {
            throw new IllegalStateException("ARMS 工具未配置全局阿里云 RAM 只读 AK");
        }
        return bindings.arms(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 ARMS Binding：" + (profile == null ? "" : profile)
        ));
    }

    private static Map<String, Object> clip(Map<String, Object> result) {
        if (String.valueOf(result).length() <= MAX_RESULT_CHARS) {
            return result;
        }
        Map<String, Object> clipped = new LinkedHashMap<>();
        clipped.put("ok", result.getOrDefault("ok", true));
        clipped.put("service", result.get("service"));
        clipped.put("action", result.get("action"));
        clipped.put("truncated", true);
        clipped.put("note", "ARMS 结果过长，已截断；请缩小时间窗或查询单条 trace");
        return clipped;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String suffix(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 8 ? text : text.substring(text.length() - 8);
    }
}
