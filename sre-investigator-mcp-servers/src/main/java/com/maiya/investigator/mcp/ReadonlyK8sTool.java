package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.config.KuboardEndpoint;
import com.maiya.investigator.mcp.k8s.KuboardClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyK8sTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyK8sTool.class);
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final int DEFAULT_LOG_TAIL = 100;
    static final int MAX_LOG_TAIL = 200;
    static final int MAX_RESULT_CHARS = 24_000;
    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;
    private final KuboardClient client;

    public ReadonlyK8sTool(BindingConnectionLookup bindings, AuditTrail auditTrail, KuboardClient client) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
        this.client = client;
    }

    public Map<String, Object> listClusters(UUID projectId, String environment, String profile) {
        KuboardEndpoint endpoint = require(projectId, environment, profile, null);
        List<String> allowed = bindings.k8sAllowedClusters(projectId, environment, profile);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("cluster", endpoint.cluster());
        payload.put("defaultCluster", endpoint.cluster());
        payload.put("kind", "clusters");
        payload.put("count", allowed.size());
        payload.put("clusters", allowed);
        return payload;
    }

    public Map<String, Object> listNamespaces(UUID projectId, String environment, String profile) {
        return listNamespaces(projectId, environment, profile, null);
    }

    public Map<String, Object> listNamespaces(UUID projectId, String environment, String profile, String cluster) {
        KuboardEndpoint endpoint = require(projectId, environment, profile, cluster);
        audit("k8s.namespaces", profile, endpoint, Map.of());
        Map<String, Object> raw = client.getJson(endpoint, "/api/v1/namespaces");
        return result(endpoint, "namespaces", summarizeNamespaces(raw));
    }

    public Map<String, Object> listNodes(UUID projectId, String environment, String profile) {
        return listNodes(projectId, environment, profile, null);
    }

    public Map<String, Object> listNodes(UUID projectId, String environment, String profile, String cluster) {
        KuboardEndpoint endpoint = require(projectId, environment, profile, cluster);
        audit("k8s.nodes", profile, endpoint, Map.of());
        Map<String, Object> raw = client.getJson(endpoint, "/api/v1/nodes");
        return result(endpoint, "nodes", summarizeNodes(raw));
    }

    public Map<String, Object> listPods(
            UUID projectId,
            String environment,
            String profile,
            String namespace,
            String labelSelector,
            String phase
    ) {
        return listPods(projectId, environment, profile, namespace, labelSelector, phase, null);
    }

    public Map<String, Object> listPods(
            UUID projectId,
            String environment,
            String profile,
            String namespace,
            String labelSelector,
            String phase,
            String cluster
    ) {
        KuboardEndpoint endpoint = require(projectId, environment, profile, cluster);
        String ns = endpoint.resolveNamespace(namespace);
        StringBuilder path = new StringBuilder("/api/v1/namespaces/").append(ns).append("/pods");
        if (labelSelector != null && !labelSelector.isBlank()) {
            path.append("?labelSelector=").append(URLEncoder.encode(labelSelector.trim(), StandardCharsets.UTF_8));
        }
        audit("k8s.pods", profile, endpoint, Map.of("namespace", ns));
        Map<String, Object> raw = client.getJson(endpoint, path.toString());
        List<Map<String, Object>> pods = summarizePods(raw, phase);
        return result(endpoint, "pods", pods);
    }

    public Map<String, Object> getPod(UUID projectId, String environment, String profile, String namespace, String name) {
        return getPod(projectId, environment, profile, namespace, name, null);
    }

    public Map<String, Object> getPod(
            UUID projectId,
            String environment,
            String profile,
            String namespace,
            String name,
            String cluster
    ) {
        KuboardEndpoint endpoint = require(projectId, environment, profile, cluster);
        String ns = endpoint.resolveNamespace(namespace);
        KuboardEndpoint.requireResourceName(name, "pod");
        audit("k8s.pod", profile, endpoint, Map.of("namespace", ns, "name", name));
        Map<String, Object> pod = client.getJson(endpoint, "/api/v1/namespaces/" + ns + "/pods/" + name);
        Map<String, Object> events = client.getJson(
                endpoint,
                "/api/v1/namespaces/" + ns + "/events?fieldSelector="
                        + URLEncoder.encode("involvedObject.name=" + name, StandardCharsets.UTF_8)
        );
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pod", summarizeOnePod(pod));
        summary.put("events", summarizeEvents(events, 20));
        return result(endpoint, "pod", List.of(summary));
    }

    public Map<String, Object> listEvents(UUID projectId, String environment, String profile, String namespace) {
        return listEvents(projectId, environment, profile, namespace, null);
    }

    public Map<String, Object> listEvents(
            UUID projectId,
            String environment,
            String profile,
            String namespace,
            String cluster
    ) {
        KuboardEndpoint endpoint = require(projectId, environment, profile, cluster);
        String ns = endpoint.resolveNamespace(namespace);
        audit("k8s.events", profile, endpoint, Map.of("namespace", ns));
        Map<String, Object> raw = client.getJson(endpoint, "/api/v1/namespaces/" + ns + "/events");
        return result(endpoint, "events", summarizeEvents(raw, MAX_LIMIT));
    }

    public Map<String, Object> podLogs(
            UUID projectId,
            String environment,
            String profile,
            String namespace,
            String name,
            String container,
            Integer tail
    ) {
        return podLogs(projectId, environment, profile, namespace, name, container, tail, null);
    }

    public Map<String, Object> podLogs(
            UUID projectId,
            String environment,
            String profile,
            String namespace,
            String name,
            String container,
            Integer tail,
            String cluster
    ) {
        KuboardEndpoint endpoint = require(projectId, environment, profile, cluster);
        String ns = endpoint.resolveNamespace(namespace);
        KuboardEndpoint.requireResourceName(name, "pod");
        int lines = QueryTimeWindow.limit(tail, DEFAULT_LOG_TAIL, MAX_LOG_TAIL);
        StringBuilder path = new StringBuilder("/api/v1/namespaces/")
                .append(ns).append("/pods/").append(name).append("/log?tailLines=").append(lines);
        if (container != null && !container.isBlank()) {
            KuboardEndpoint.requireResourceName(container.trim(), "container");
            path.append("&container=").append(URLEncoder.encode(container.trim(), StandardCharsets.UTF_8));
        }
        audit("k8s.logs", profile, endpoint, Map.of("namespace", ns, "name", name));
        String text = client.getText(endpoint, path.toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("cluster", endpoint.cluster());
        payload.put("namespace", ns);
        payload.put("pod", name);
        payload.put("tail", lines);
        payload.put("log", clipText(text, MAX_RESULT_CHARS));
        if (text != null && text.length() > MAX_RESULT_CHARS) {
            payload.put("truncated", true);
        }
        return payload;
    }

    private KuboardEndpoint require(UUID projectId, String environment, String profile) {
        return require(projectId, environment, profile, null);
    }

    private KuboardEndpoint require(UUID projectId, String environment, String profile, String cluster) {
        if (projectId == null) {
            throw new IllegalStateException("K8s 只使用 MCP 页保存的 Kuboard 连接，当前没有项目上下文");
        }
        return bindings.k8s(projectId, environment, profile, cluster).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 MCP K8s：" + (profile == null ? "" : profile)
                        + "。请到 MCP 页保存 Kuboard Binding 并点「测试连接」。"
        ));
    }

    private void audit(String action, String profile, KuboardEndpoint endpoint, Map<String, String> extra) {
        log.info("K8s 只读 {} profile={} cluster={}", action, profile, endpoint.cluster());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cluster", endpoint.cluster());
        payload.put("host", endpoint.host());
        payload.putAll(extra);
        auditTrail.record(action, profile == null ? "" : profile, payload);
    }

    private static Map<String, Object> result(KuboardEndpoint endpoint, String kind, List<Map<String, Object>> items) {
        List<Map<String, Object>> capped = items.size() > MAX_LIMIT ? items.subList(0, MAX_LIMIT) : items;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("cluster", endpoint.cluster());
        payload.put("kind", kind);
        payload.put("count", capped.size());
        payload.put(kind, capped);
        if (capped.size() < items.size()) {
            payload.put("truncated", true);
        }
        return clip(payload);
    }

    static List<Map<String, Object>> summarizeNamespaces(Map<String, Object> raw) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : itemsOf(raw)) {
            Map<String, Object> meta = map(item.get("metadata"));
            items.add(Map.of("name", text(meta.get("name"))));
        }
        return items;
    }

    static List<Map<String, Object>> summarizeNodes(Map<String, Object> raw) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : itemsOf(raw)) {
            Map<String, Object> meta = map(item.get("metadata"));
            Map<String, Object> status = map(item.get("status"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", text(meta.get("name")));
            row.put("ready", ready(status.get("conditions")));
            row.put("addresses", addresses(status.get("addresses")));
            items.add(row);
        }
        return items;
    }

    static List<Map<String, Object>> summarizePods(Map<String, Object> raw, String phaseFilter) {
        String wanted = phaseFilter == null ? "" : phaseFilter.trim();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : itemsOf(raw)) {
            Map<String, Object> row = summarizeOnePod(item);
            if (!wanted.isBlank() && !wanted.equalsIgnoreCase(String.valueOf(row.get("phase")))) {
                continue;
            }
            items.add(row);
            if (items.size() >= MAX_LIMIT) {
                break;
            }
        }
        return items;
    }

    static Map<String, Object> summarizeOnePod(Map<String, Object> item) {
        Map<String, Object> meta = map(item.get("metadata"));
        Map<String, Object> spec = map(item.get("spec"));
        Map<String, Object> status = map(item.get("status"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", text(meta.get("name")));
        row.put("namespace", text(meta.get("namespace")));
        row.put("phase", text(status.get("phase")));
        row.put("node", text(spec.get("nodeName")));
        row.put("restarts", restartCount(status.get("containerStatuses")));
        row.put("containers", containerHints(status.get("containerStatuses")));
        return row;
    }

    static List<Map<String, Object>> summarizeEvents(Map<String, Object> raw, int limit) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        List<Map<String, Object>> others = new ArrayList<>();
        for (Map<String, Object> item : itemsOf(raw)) {
            Map<String, Object> obj = map(item.get("involvedObject"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", text(item.get("type")));
            row.put("reason", text(item.get("reason")));
            row.put("message", clipText(text(item.get("message")), 400));
            row.put("object", text(obj.get("kind")) + "/" + text(obj.get("name")));
            row.put("lastTimestamp", firstText(item.get("lastTimestamp"), item.get("eventTime")));
            if ("Warning".equalsIgnoreCase(text(item.get("type")))) {
                warnings.add(row);
            } else {
                others.add(row);
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>(warnings);
        merged.addAll(others);
        return merged.size() > limit ? merged.subList(0, limit) : merged;
    }

    private static List<Map<String, Object>> itemsOf(Map<String, Object> raw) {
        Object items = raw == null ? null : raw.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                mapped.add(copy);
            }
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String ready(Object conditions) {
        if (!(conditions instanceof List<?> list)) {
            return "Unknown";
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && "Ready".equals(String.valueOf(map.get("type")))) {
                return String.valueOf(map.get("status"));
            }
        }
        return "Unknown";
    }

    private static List<String> addresses(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                values.add(map.get("type") + "=" + map.get("address"));
            }
        }
        return values;
    }

    private static int restartCount(Object statuses) {
        if (!(statuses instanceof List<?> list)) {
            return 0;
        }
        int total = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("restartCount") instanceof Number number) {
                total += number.intValue();
            }
        }
        return total;
    }

    private static List<String> containerHints(Object statuses) {
        if (!(statuses instanceof List<?> list)) {
            return List.of();
        }
        List<String> hints = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = String.valueOf(map.get("name"));
            String waiting = waitingReason(map.get("state"));
            hints.add(name + (waiting.isBlank() ? "" : "=" + waiting));
        }
        return hints;
    }

    private static String waitingReason(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return "";
        }
        Object waiting = map.get("waiting");
        if (waiting instanceof Map<?, ?> wait) {
            return text(wait.get("reason"));
        }
        Object terminated = map.get("terminated");
        if (terminated instanceof Map<?, ?> term) {
            return text(term.get("reason"));
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstText(Object left, Object right) {
        String value = text(left);
        return value.isBlank() ? text(right) : value;
    }

    private static String clipText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    static Map<String, Object> clip(Map<String, Object> result) {
        String json = String.valueOf(result);
        if (json.length() <= MAX_RESULT_CHARS) {
            return result;
        }
        Map<String, Object> clipped = new LinkedHashMap<>(result);
        clipped.put("truncated", true);
        clipped.put("note", "结果过长，已截断进 prompt");
        return clipped;
    }
}
