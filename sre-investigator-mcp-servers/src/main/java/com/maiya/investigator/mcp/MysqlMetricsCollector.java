package com.maiya.investigator.mcp;

import com.maiya.investigator.api.Environment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MySQL 只读指标采集器。
 *
 * <p>所有查询走 {@link ReadonlyMysqlTool#query(UUID, String, String, String)}，
 * 内部经 {@link ReadOnlySqlGuard} 守卫，只允许 SHOW / SELECT。单项查询失败不影响整体，
 * 对应指标标记为 unavailable。
 */
@Service
public class MysqlMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(MysqlMetricsCollector.class);

    public static final String KEY_CONNECTIONS = "connections";
    public static final String KEY_THREADS_RUNNING = "threads_running";
    public static final String KEY_MEMORY_USAGE = "memory_usage";
    public static final String KEY_SLOW_QUERIES = "slow_queries";
    public static final String KEY_QPS = "qps";
    public static final String KEY_INNODB_DISK_READS = "innodb_disk_reads";
    public static final String KEY_QUESTIONS = "questions";

    private final ReadonlyMysqlTool mysql;

    public MysqlMetricsCollector(ReadonlyMysqlTool mysql) {
        this.mysql = mysql;
    }

    /**
     * 采集指定 binding 的当前指标快照。
     *
     * @param projectId     项目 ID
     * @param environment   环境
     * @param profile       binding profile
     * @param host          数据库主机地址，用于判断是否为本机实例；可为 null
     * @param previousQuestions 上一次采集的 Questions 值，用于计算 QPS；无历史传 null
     * @param previousCollectedAt 上一次采集时间，用于计算 QPS；无历史传 null
     */
    public MetricSnapshot collect(
            UUID projectId,
            Environment environment,
            String profile,
            String host,
            Long previousQuestions,
            Instant previousCollectedAt
    ) {
        String env = environment.code();
        List<MetricPoint> points = new ArrayList<>();

        long threadsConnected = statusValue(projectId, env, profile, "Threads_connected");
        long maxConnections = variableValue(projectId, env, profile, "max_connections");
        long threadsRunning = statusValue(projectId, env, profile, "Threads_running");
        long bufferData = statusValue(projectId, env, profile, "Innodb_buffer_pool_bytes_data");
        long bufferPoolSize = variableValue(projectId, env, profile, "innodb_buffer_pool_size");
        long slowQueries = globalStatusValue(projectId, env, profile, "Slow_queries");
        long questions = globalStatusValue(projectId, env, profile, "Questions");
        long innodbReads = statusValue(projectId, env, profile, "Innodb_buffer_pool_reads");

        points.add(OsMetrics.cpuPoint(host));
        points.add(OsMetrics.memoryPoint(host));
        points.add(point(KEY_CONNECTIONS, "连接数", threadsConnected >= 0 ? String.valueOf(threadsConnected) : "unavailable",
                maxConnections > 0 ? threadsConnected + "/" + maxConnections : "个"));
        points.add(point(KEY_THREADS_RUNNING, "活跃线程（负载代理）", threadsRunning >= 0 ? String.valueOf(threadsRunning) : "unavailable", "个"));

        if (bufferPoolSize > 0 && bufferData >= 0) {
            double pct = bufferData * 100.0 / bufferPoolSize;
            points.add(point(KEY_MEMORY_USAGE, "InnoDB 缓冲池使用率", String.format("%.1f", pct), "%"));
        } else {
            points.add(point(KEY_MEMORY_USAGE, "InnoDB 缓冲池使用率", "unavailable", "%"));
        }

        points.add(point(KEY_SLOW_QUERIES, "慢查询累计", slowQueries >= 0 ? String.valueOf(slowQueries) : "unavailable", "次"));
        points.add(point(KEY_INNODB_DISK_READS, "InnoDB 磁盘读", innodbReads >= 0 ? String.valueOf(innodbReads) : "unavailable", "次"));

        if (previousQuestions != null && previousQuestions >= 0 && questions >= 0 && previousCollectedAt != null) {
            long elapsed = Instant.now().getEpochSecond() - previousCollectedAt.getEpochSecond();
            if (elapsed > 0 && questions >= previousQuestions) {
                double qps = (double) (questions - previousQuestions) / elapsed;
                points.add(point(KEY_QPS, "QPS", String.format("%.1f", qps), "/s"));
            } else {
                points.add(point(KEY_QPS, "QPS", "unavailable", "/s"));
            }
        } else {
            points.add(point(KEY_QPS, "QPS", "unavailable（首次采集）", "/s"));
        }

        points.add(point(KEY_QUESTIONS, "Questions 累计", questions >= 0 ? String.valueOf(questions) : "unavailable", "次"));

        return new MetricSnapshot(points);
    }

    /**
     * 分页查询最近的慢 SQL 明细（来自 performance_schema 语句事件历史）。
     *
     * <p>时间点由 TIMER_END 相对服务器启动时长换算为墙钟时间；执行用户依赖 threads 表，
     * 无 PROCESS 权限时为 null。
     */
    public SlowQueryPage slowQueries(
            UUID projectId,
            Environment environment,
            String profile,
            int limit,
            int offset,
            double minSeconds
    ) {
        String env = environment.code();
        String where = "WHERE h.TIMER_WAIT IS NOT NULL "
                + "AND h.TIMER_WAIT / 1000000000000 >= " + minSeconds + " "
                + "AND h.SQL_TEXT NOT LIKE 'SHOW %' "
                + "AND h.SQL_TEXT NOT LIKE '%performance_schema%' ";
        String countSql = "SELECT COUNT(*) AS total "
                + "FROM performance_schema.events_statements_history h "
                + "JOIN performance_schema.threads t ON h.THREAD_ID = t.THREAD_ID "
                + where;
        String dataSql = "SELECT h.SQL_TEXT AS sql_text, "
                + "FROM_UNIXTIME(UNIX_TIMESTAMP(NOW()) - (SELECT VARIABLE_VALUE FROM performance_schema.global_status "
                + "WHERE VARIABLE_NAME = 'Uptime') + h.TIMER_END / 1000000000000) AS executed_at, "
                + "t.PROCESSLIST_USER AS exec_user, "
                + "ROUND(h.TIMER_WAIT / 1000000000000, 4) AS seconds "
                + "FROM performance_schema.events_statements_history h "
                + "JOIN performance_schema.threads t ON h.THREAD_ID = t.THREAD_ID "
                + where
                + "ORDER BY h.TIMER_WAIT DESC "
                + "LIMIT " + limit + " OFFSET " + offset;
        try {
            long total = 0;
            List<Map<String, Object>> countRows = mysql.query(projectId, env, profile, countSql);
            if (!countRows.isEmpty() && countRows.get(0).get("total") != null) {
                total = Long.parseLong(String.valueOf(countRows.get(0).get("total")).trim());
            }
            List<SlowQueryRow> rows = new ArrayList<>();
            for (Map<String, Object> row : mysql.query(projectId, env, profile, dataSql)) {
                Object user = row.get("exec_user");
                rows.add(new SlowQueryRow(
                        clip(String.valueOf(row.getOrDefault("sql_text", "")), 2000),
                        formatTimestamp(row.get("executed_at")),
                        user == null ? null : String.valueOf(user),
                        parseSeconds(row.get("seconds"))
                ));
            }
            return new SlowQueryPage(rows, total, null);
        } catch (RuntimeException exception) {
            log.debug("查询慢 SQL 明细失败：{}", exception.getMessage());
            return new SlowQueryPage(List.of(), 0, "账号无 performance_schema 权限或语句历史未开启");
        }
    }

    private long statusValue(UUID projectId, String env, String profile, String name) {
        return singleValue("SHOW STATUS LIKE '" + name + "'", projectId, env, profile);
    }

    private long globalStatusValue(UUID projectId, String env, String profile, String name) {
        return singleValue("SHOW GLOBAL STATUS LIKE '" + name + "'", projectId, env, profile);
    }

    private long variableValue(UUID projectId, String env, String profile, String name) {
        return singleValue("SHOW VARIABLES LIKE '" + name + "'", projectId, env, profile);
    }

    private long singleValue(String sql, UUID projectId, String env, String profile) {
        try {
            List<Map<String, Object>> rows = mysql.query(projectId, env, profile, sql);
            if (rows.isEmpty()) {
                return -1;
            }
            Object value = rows.get(0).get("Value");
            if (value == null) {
                return -1;
            }
            return Long.parseLong(String.valueOf(value).trim());
        } catch (RuntimeException exception) {
            log.debug("采集 MySQL 指标失败 sql={}：{}", sql, exception.getMessage());
            return -1;
        }
    }

    private static MetricPoint point(String key, String label, String value, String unit) {
        return new MetricPoint(key, label, value, unit);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "…";
    }

    private static String formatTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).replace('T', ' ').replaceAll("\\.\\d+$", "");
    }

    private static double parseSeconds(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public record SlowQueryRow(String sql, String executedAt, String user, double seconds) {
    }

    public record SlowQueryPage(List<SlowQueryRow> rows, long total, String note) {
    }
}
