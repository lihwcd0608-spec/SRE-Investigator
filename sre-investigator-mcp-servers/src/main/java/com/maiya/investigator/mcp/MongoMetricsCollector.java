package com.maiya.investigator.mcp;

import com.maiya.investigator.api.Environment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MongoDB 只读指标采集器。
 *
 * <p>仅使用 serverStatus / dbStats / hello 等只读命令，经 {@link ReadonlyMongodbTool} 访问，
 * 不进入 Agent 工具循环。单项失败不影响整体，对应指标标记为 unavailable。
 */
@Service
public class MongoMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(MongoMetricsCollector.class);

    public static final String KEY_CONNECTIONS = "connections";
    public static final String KEY_CPU_USAGE = "cpu_usage";
    public static final String KEY_OS_MEMORY = "os_memory";
    public static final String KEY_MEMORY_RESIDENT = "memory_resident";
    public static final String KEY_OPS_QUERY = "ops_query";
    public static final String KEY_NETWORK_IO = "network_io";
    public static final String KEY_DB_OBJECTS = "db_objects";
    public static final String KEY_DB_SIZE = "db_size";
    public static final String KEY_STORAGE_SIZE = "storage_size";
    public static final String KEY_INDEX_SIZE = "index_size";
    public static final String KEY_REPLICA_SET = "replica_set";
    public static final String KEY_UPTIME = "uptime";

    private final ReadonlyMongodbTool mongodb;

    public MongoMetricsCollector(ReadonlyMongodbTool mongodb) {
        this.mongodb = mongodb;
    }

    /**
     * 采集 MongoDB 指标快照。
     *
     * @param host 数据库主机地址，用于判断是否为本机实例；可为 null
     */
    public MetricSnapshot collect(UUID projectId, Environment environment, String profile, String host) {
        List<MetricPoint> points = new ArrayList<>();
        points.add(OsMetrics.cpuPoint(host));
        points.add(OsMetrics.memoryPoint(host));

        Map<String, Object> raw;
        try {
            raw = mongodb.metricsInfo(projectId, environment.code(), profile);
        } catch (RuntimeException exception) {
            log.debug("MongoDB 监控采集失败：{}", exception.getMessage());
            raw = Map.of();
        }
        Document serverStatus = doc(raw.get("serverStatus"));
        Document hello = doc(raw.get("hello"));
        Document dbStats = doc(raw.get("dbStats"));

        Document connections = doc(serverStatus.get("connections"));
        long current = longOf(connections.get("current"));
        long available = longOf(connections.get("available"));
        if (current >= 0) {
            long capacity = current + Math.max(available, 0);
            points.add(point(KEY_CONNECTIONS, "连接数",
                    capacity > 0 ? current + "/" + capacity : String.valueOf(current), "个"));
        } else {
            points.add(point(KEY_CONNECTIONS, "连接数", "unavailable", "个"));
        }

        Document mem = doc(serverStatus.get("mem"));
        long resident = longOf(mem.get("resident"));
        points.add(point(KEY_MEMORY_RESIDENT, "物理内存占用",
                resident >= 0 ? String.valueOf(resident) : "unavailable", "MB"));

        Document opcounters = doc(serverStatus.get("opcounters"));
        long queries = longOf(opcounters.get("query"));
        points.add(point(KEY_OPS_QUERY, "查询累计", queries >= 0 ? String.valueOf(queries) : "unavailable", "次"));

        Document network = doc(serverStatus.get("network"));
        long bytesIn = longOf(network.get("bytesIn"));
        long bytesOut = longOf(network.get("bytesOut"));
        if (bytesIn >= 0 && bytesOut >= 0) {
            points.add(point(KEY_NETWORK_IO, "网络吞吐累计",
                    String.format("%.1f", (bytesIn + bytesOut) / 1048576.0), "MB"));
        } else {
            points.add(point(KEY_NETWORK_IO, "网络吞吐累计", "unavailable", "MB"));
        }

        if (!dbStats.isEmpty()) {
            points.add(point(KEY_DB_OBJECTS, "文档总数（默认库）",
                    longOf(dbStats.get("objects")) >= 0 ? String.valueOf(longOf(dbStats.get("objects"))) : "unavailable", "条"));
            points.add(mbPoint(KEY_DB_SIZE, "数据大小（默认库）", doubleOf(dbStats.get("dataSize"))));
            points.add(mbPoint(KEY_STORAGE_SIZE, "存储占用（默认库）", doubleOf(dbStats.get("storageSize"))));
            points.add(mbPoint(KEY_INDEX_SIZE, "索引大小（默认库）", doubleOf(dbStats.get("indexSize"))));
        } else {
            points.add(point(KEY_DB_OBJECTS, "文档总数（默认库）", "unavailable", "条"));
            points.add(point(KEY_DB_SIZE, "数据大小（默认库）", "unavailable", "MB"));
            points.add(point(KEY_STORAGE_SIZE, "存储占用（默认库）", "unavailable", "MB"));
            points.add(point(KEY_INDEX_SIZE, "索引大小（默认库）", "unavailable", "MB"));
        }

        String setName = text(hello.get("setName"));
        points.add(point(KEY_REPLICA_SET, "部署形态",
                hello.isEmpty() ? "unavailable" : setName.isBlank() ? "单机" : "副本集 " + setName, ""));

        long uptime = longOf(serverStatus.get("uptime"));
        if (uptime >= 0) {
            points.add(point(KEY_UPTIME, "运行时长", String.format("%.1f", uptime / 86400.0), "天"));
        } else {
            points.add(point(KEY_UPTIME, "运行时长", "unavailable", "天"));
        }

        return new MetricSnapshot(points);
    }

    private static MetricPoint point(String key, String label, String value, String unit) {
        return new MetricPoint(key, label, value, unit);
    }

    private static MetricPoint mbPoint(String key, String label, double value) {
        return value >= 0
                ? point(key, label, String.format("%.1f", value), "MB")
                : point(key, label, "unavailable", "MB");
    }

    private static Document doc(Object value) {
        return value instanceof Document document ? document : new Document();
    }

    private static long longOf(Object value) {
        double parsed = doubleOf(value);
        return parsed >= 0 ? Math.round(parsed) : -1;
    }

    private static double doubleOf(Object value) {
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
