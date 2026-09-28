package com.maiya.investigator.boot.monitor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.infra.persistence.BindingMetricEntity;
import com.maiya.investigator.infra.persistence.BindingMetricJpaRepository;
import com.maiya.investigator.infra.security.SecretCipher;
import com.maiya.investigator.mcp.ElasticsearchMetricsCollector;
import com.maiya.investigator.mcp.MongoMetricsCollector;
import com.maiya.investigator.mcp.MetricPoint;
import com.maiya.investigator.mcp.MetricSnapshot;
import com.maiya.investigator.mcp.MysqlMetricsCollector;
import com.maiya.investigator.mcp.RedisMetricsCollector;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class BindingMetricsService {
    private final ControlPlaneStore controlPlane;
    private final MysqlMetricsCollector mysqlCollector;
    private final RedisMetricsCollector redisCollector;
    private final ElasticsearchMetricsCollector elasticsearchCollector;
    private final MongoMetricsCollector mongoCollector;
    private final BindingMetricJpaRepository metrics;
    private final SecretCipher cipher;
    private final ObjectMapper objectMapper;

    public BindingMetricsService(
            ControlPlaneStore controlPlane,
            MysqlMetricsCollector mysqlCollector,
            RedisMetricsCollector redisCollector,
            ElasticsearchMetricsCollector elasticsearchCollector,
            MongoMetricsCollector mongoCollector,
            BindingMetricJpaRepository metrics,
            SecretCipher cipher,
            ObjectMapper objectMapper
    ) {
        this.controlPlane = controlPlane;
        this.mysqlCollector = mysqlCollector;
        this.redisCollector = redisCollector;
        this.elasticsearchCollector = elasticsearchCollector;
        this.mongoCollector = mongoCollector;
        this.metrics = metrics;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    /** 采集指定 binding 的当前快照（用于 API 按需查询）。 */
    public MetricSnapshot snapshot(UUID projectId, UUID bindingId) {
        ToolBinding binding = controlPlane.binding(projectId, bindingId);
        return switch (binding.toolKind()) {
            case MYSQL -> {
                Long previousQuestions = lastMetricValue(bindingId, MysqlMetricsCollector.KEY_QUESTIONS);
                Instant previousAt = lastMetricCollectedAt(bindingId, MysqlMetricsCollector.KEY_QUESTIONS);
                yield mysqlCollector.collect(projectId, binding.environment(), binding.profile(),
                        bindingHost(bindingId), previousQuestions, previousAt);
            }
            case REDIS -> redisCollector.collect(projectId, binding.environment(), binding.profile(),
                    bindingHost(bindingId));
            case ELASTICSEARCH -> elasticsearchCollector.collect(projectId, binding.environment(), binding.profile());
            case MONGODB -> mongoCollector.collect(projectId, binding.environment(), binding.profile(),
                    bindingHost(bindingId));
            default -> throw new IllegalArgumentException("该绑定类型暂不支持监控：" + binding.toolKind());
        };
    }

    /** 分页查询最近慢 SQL 明细（仅 MySQL）。 */
    public MysqlMetricsCollector.SlowQueryPage slowQueries(UUID projectId, UUID bindingId, int page, int size, int minMillis) {
        ToolBinding binding = controlPlane.binding(projectId, bindingId);
        if (binding.toolKind() != ToolKind.MYSQL) {
            return new MysqlMetricsCollector.SlowQueryPage(List.of(), 0, "仅 MySQL 支持慢 SQL 明细");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        double minSeconds = Math.max(minMillis, 0) / 1000.0;
        return mysqlCollector.slowQueries(projectId, binding.environment(), binding.profile(),
                safeSize, (safePage - 1) * safeSize, minSeconds);
    }

    /** 从 binding 配置中解析数据库主机地址，用于判断是否为本机实例。 */
    private String bindingHost(UUID bindingId) {
        String encrypted = controlPlane.encryptedBindingConfiguration(bindingId);
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(
                    cipher.decrypt(encrypted), new TypeReference<>() {
                    });
            Object host = config == null ? null : config.get("host");
            return host == null ? null : String.valueOf(host);
        } catch (Exception exception) {
            return null;
        }
    }

    /** 查询某指标的最近 N 条历史。 */
    public List<MetricHistoryPoint> history(UUID projectId, UUID bindingId, String metricKey, int limit) {
        controlPlane.binding(projectId, bindingId);
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return metrics.findByBindingIdAndMetricKeyOrderByCollectedAtDesc(bindingId, metricKey, PageRequest.of(0, safeLimit))
                .stream()
                .map(entity -> new MetricHistoryPoint(entity.getMetricValue(), entity.getCollectedAt()))
                .toList();
    }

    /** 把快照中的数值型指标写入时序表（供调度器调用）。 */
    public void store(UUID bindingId, MetricSnapshot snapshot) {
        Instant now = Instant.now();
        for (MetricPoint point : snapshot.points()) {
            if (point.value() == null || point.value().startsWith("unavailable")) {
                continue;
            }
            String numeric = extractNumeric(point.value());
            if (numeric == null) {
                continue;
            }
            metrics.save(new BindingMetricEntity(
                    UUID.randomUUID(), bindingId, point.key(), numeric, now
            ));
        }
    }

    /** 清理超过 retentionHours 的历史数据。 */
    public void cleanupOlderThan(int retentionHours) {
        metrics.deleteByCollectedAtBefore(Instant.now().minusSeconds((long) retentionHours * 3600));
    }

    private Long lastMetricValue(UUID bindingId, String metricKey) {
        return metrics.findByBindingIdAndMetricKeyOrderByCollectedAtDesc(bindingId, metricKey, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(entity -> {
                    try {
                        return Long.parseLong(entity.getMetricValue());
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private Instant lastMetricCollectedAt(UUID bindingId, String metricKey) {
        return metrics.findByBindingIdAndMetricKeyOrderByCollectedAtDesc(bindingId, metricKey, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(BindingMetricEntity::getCollectedAt)
                .orElse(null);
    }

    /** 从 "12/100" 或 "85.5" 这类字符串中提取可持久化的数值部分。 */
    private static String extractNumeric(String value) {
        String trimmed = value.trim();
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        int slash = trimmed.indexOf('/');
        if (slash > 0) {
            String head = trimmed.substring(0, slash).trim();
            if (head.matches("-?\\d+(\\.\\d+)?")) {
                return head;
            }
        }
        return null;
    }

    public record MetricHistoryPoint(String value, Instant collectedAt) {
    }
}
