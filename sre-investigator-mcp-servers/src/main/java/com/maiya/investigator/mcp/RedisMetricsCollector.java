package com.maiya.investigator.mcp;

import com.maiya.investigator.api.Environment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Redis 只读指标采集器。
 *
 * <p>仅使用 INFO / DBSIZE 等只读命令，经 {@link ReadonlyRedisTool} 访问，
 * 不进入 Agent 工具循环。单项失败整体降级为 unavailable。
 */
@Service
public class RedisMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(RedisMetricsCollector.class);

    public static final String KEY_CONNECTIONS = "connections";
    public static final String KEY_CPU_USAGE = "cpu_usage";
    public static final String KEY_OS_MEMORY = "os_memory";
    public static final String KEY_MEMORY_USAGE = "memory_usage";
    public static final String KEY_MEMORY_RSS = "memory_rss";
    public static final String KEY_OPS = "ops_per_sec";
    public static final String KEY_HIT_RATE = "hit_rate";
    public static final String KEY_TOTAL_KEYS = "total_keys";
    public static final String KEY_EVICTED = "evicted_keys";
    public static final String KEY_REJECTED = "rejected_connections";

    private final ReadonlyRedisTool redis;

    public RedisMetricsCollector(ReadonlyRedisTool redis) {
        this.redis = redis;
    }

    /**
     * 采集 Redis 指标快照。
     *
     * @param host 数据库主机地址，用于判断是否为本机实例；可为 null
     */
    public MetricSnapshot collect(UUID projectId, Environment environment, String profile, String host) {
        final var ref = new Object() {
            Map<String, String> info;
        };
        long dbsize = -1;
        try {
            Map<String, Object> raw = redis.metricsInfo(projectId, environment.code(), profile);
            Object size = raw.remove("__dbsize");
            dbsize = parseLong(size);
            ref.info = new LinkedHashMap<>();
            raw.forEach((key, value) -> ref.info.put(key, value == null ? "" : String.valueOf(value)));
        } catch (RuntimeException exception) {
            log.debug("Redis INFO 采集失败：{}", exception.getMessage());
            ref.info = Map.of();
        }

        List<MetricPoint> points = new ArrayList<>();
        points.add(OsMetrics.cpuPoint(host));
        points.add(OsMetrics.memoryPoint(host));

        long clients = parseLong(ref.info.get("connected_clients"));
        long maxClients = parseLong(ref.info.get("maxclients"));
        points.add(point(KEY_CONNECTIONS, "连接数",
                clients >= 0 ? String.valueOf(clients) : "unavailable",
                maxClients > 0 ? clients + "/" + maxClients : "个"));

        long usedMemory = parseLong(ref.info.get("used_memory"));
        long maxMemory = parseLong(ref.info.get("maxmemory"));
        if (maxMemory > 0 && usedMemory >= 0) {
            points.add(point(KEY_MEMORY_USAGE, "内存使用率（maxmemory）",
                    String.format("%.1f", usedMemory * 100.0 / maxMemory), "%"));
        } else {
            points.add(point(KEY_MEMORY_USAGE, "内存使用率（maxmemory）", "unavailable", "%"));
        }

        long rss = parseLong(ref.info.get("used_memory_rss"));
        points.add(point(KEY_MEMORY_RSS, "物理内存占用",
                rss >= 0 ? String.format("%.1f", rss / 1048576.0) : "unavailable", "MB"));

        long ops = parseLong(ref.info.get("instantaneous_ops_per_sec"));
        points.add(point(KEY_OPS, "OPS", ops >= 0 ? String.valueOf(ops) : "unavailable", "/s"));

        long hits = parseLong(ref.info.get("keyspace_hits"));
        long misses = parseLong(ref.info.get("keyspace_misses"));
        if (hits >= 0 && misses >= 0 && hits + misses > 0) {
            points.add(point(KEY_HIT_RATE, "缓存命中率",
                    String.format("%.1f", hits * 100.0 / (hits + misses)), "%"));
        } else {
            points.add(point(KEY_HIT_RATE, "缓存命中率", "unavailable", "%"));
        }

        points.add(point(KEY_TOTAL_KEYS, "Key 总数", dbsize >= 0 ? String.valueOf(dbsize) : "unavailable", "个"));

        long evicted = parseLong(ref.info.get("evicted_keys"));
        points.add(point(KEY_EVICTED, "驱逐 Key 累计", evicted >= 0 ? String.valueOf(evicted) : "unavailable", "个"));

        long rejected = parseLong(ref.info.get("rejected_connections"));
        points.add(point(KEY_REJECTED, "拒绝连接累计", rejected >= 0 ? String.valueOf(rejected) : "unavailable", "次"));

        return new MetricSnapshot(points);
    }

    private static MetricPoint point(String key, String label, String value, String unit) {
        return new MetricPoint(key, label, value, unit);
    }

    private static long parseLong(Object value) {
        if (value == null) {
            return -1;
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.endsWith("b") || text.endsWith("B")) {
                text = text.substring(0, text.length() - 1);
            }
            return (long) Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
