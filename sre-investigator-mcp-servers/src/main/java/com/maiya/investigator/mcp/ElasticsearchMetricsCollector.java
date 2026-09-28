package com.maiya.investigator.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.Environment;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 只读指标采集器。
 *
 * <p>仅调用集群只读 REST 接口（_cluster/health、_nodes/stats、_cluster/stats），
 * 不进入 Agent 工具循环。单项请求失败不影响整体，对应指标标记为 unavailable。
 */
@Service
public class ElasticsearchMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchMetricsCollector.class);

    public static final String KEY_CLUSTER_STATUS = "cluster_status";
    public static final String KEY_NODES = "nodes_count";
    public static final String KEY_CPU_USAGE = "cpu_usage";
    public static final String KEY_OS_MEMORY = "os_memory";
    public static final String KEY_HEAP_USAGE = "heap_usage";
    public static final String KEY_DISK_USAGE = "disk_usage";
    public static final String KEY_ACTIVE_SHARDS = "active_shards";
    public static final String KEY_UNASSIGNED = "unassigned_shards";
    public static final String KEY_DOCS = "docs_count";

    private static final Duration TIMEOUT = Duration.ofMillis(5000);

    private final BindingConnectionLookup bindings;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public ElasticsearchMetricsCollector(BindingConnectionLookup bindings, ObjectMapper mapper) {
        this.bindings = bindings;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public MetricSnapshot collect(UUID projectId, Environment environment, String profile) {
        String env = environment.code();
        List<MetricPoint> points = new ArrayList<>();

        Map<String, Object> health = getJson(projectId, env, profile, "/_cluster/health");
        String status = text(health.get("status"));
        points.add(point(KEY_CLUSTER_STATUS, "集群状态", status.isBlank() ? "unavailable" : status, ""));
        long nodes = longValue(health.get("number_of_nodes"));
        points.add(point(KEY_NODES, "节点数", nodes >= 0 ? String.valueOf(nodes) : "unavailable", "个"));
        long active = longValue(health.get("active_shards"));
        points.add(point(KEY_ACTIVE_SHARDS, "活跃分片", active >= 0 ? String.valueOf(active) : "unavailable", "个"));
        long unassigned = longValue(health.get("unassigned_shards"));
        points.add(point(KEY_UNASSIGNED, "未分配分片", unassigned >= 0 ? String.valueOf(unassigned) : "unavailable", "个"));

        Map<String, Object> stats = getJson(projectId, env, profile, "/_nodes/stats/os,jvm,fs");
        double maxCpu = -1;
        double maxMem = -1;
        double maxHeap = -1;
        double maxDisk = -1;
        for (Object nodeValue : map(stats.get("nodes")).values()) {
            Map<String, Object> node = map(nodeValue);
            double cpu = doubleValue(path(node, "os", "cpu", "percent"));
            if (cpu >= 0) {
                maxCpu = Math.max(maxCpu, cpu);
            }
            double mem = doubleValue(path(node, "os", "mem", "used_percent"));
            if (mem < 0) {
                long memTotal = longValue(path(node, "os", "mem", "total_in_bytes"));
                long memUsed = longValue(path(node, "os", "mem", "used_in_bytes"));
                if (memTotal > 0 && memUsed >= 0) {
                    mem = memUsed * 100.0 / memTotal;
                }
            }
            if (mem >= 0) {
                maxMem = Math.max(maxMem, mem);
            }
            double heap = doubleValue(path(node, "jvm", "mem", "heap_used_percent"));
            if (heap >= 0) {
                maxHeap = Math.max(maxHeap, heap);
            }
            long diskTotal = longValue(path(node, "fs", "total", "total_in_bytes"));
            long diskFree = longValue(path(node, "fs", "total", "free_in_bytes"));
            if (diskTotal > 0 && diskFree >= 0) {
                maxDisk = Math.max(maxDisk, (diskTotal - diskFree) * 100.0 / diskTotal);
            }
        }
        points.add(percentPoint(KEY_CPU_USAGE, "CPU 占用率（节点峰值）", maxCpu));
        points.add(percentPoint(KEY_OS_MEMORY, "内存占用率（节点峰值）", maxMem));
        points.add(percentPoint(KEY_HEAP_USAGE, "JVM 堆内存（节点峰值）", maxHeap));
        points.add(percentPoint(KEY_DISK_USAGE, "磁盘使用率（节点峰值）", maxDisk));

        Map<String, Object> clusterStats = getJson(projectId, env, profile, "/_cluster/stats");
        long docs = longValue(path(clusterStats, "indices", "docs", "count"));
        points.add(point(KEY_DOCS, "文档总数", docs >= 0 ? String.valueOf(docs) : "unavailable", "条"));

        return new MetricSnapshot(points);
    }

    private Map<String, Object> getJson(UUID projectId, String env, String profile, String path) {
        try {
            BindingConnectionLookup.EsEndpoint endpoint = bindings.elasticsearch(projectId, env, profile)
                    .orElseThrow(() -> new IllegalStateException("未找到 Elasticsearch 绑定配置"));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint.baseUrl() + path))
                    .timeout(TIMEOUT)
                    .GET();
            if (!endpoint.username().isBlank()) {
                String token = Base64.getEncoder().encodeToString(
                        (endpoint.username() + ":" + (endpoint.password() == null ? "" : endpoint.password()))
                                .getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Elasticsearch HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(),
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception exception) {
            log.debug("Elasticsearch 指标采集失败 path={}：{}", path, exception.getMessage());
            return Map.of();
        }
    }

    private static MetricPoint point(String key, String label, String value, String unit) {
        return new MetricPoint(key, label, value, unit);
    }

    private static MetricPoint percentPoint(String key, String label, double value) {
        return value >= 0
                ? point(key, label, String.format("%.1f", value), "%")
                : point(key, label, "unavailable", "%");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static Object path(Map<String, Object> source, Object... keys) {
        Object current = source;
        for (Object key : keys) {
            if (!(current instanceof Map<?, ?> mapValue)) {
                return null;
            }
            current = mapValue.get(String.valueOf(key));
        }
        return current;
    }

    private static long longValue(Object value) {
        double parsed = doubleValue(value);
        return parsed >= 0 ? Math.round(parsed) : -1;
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return -1;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
