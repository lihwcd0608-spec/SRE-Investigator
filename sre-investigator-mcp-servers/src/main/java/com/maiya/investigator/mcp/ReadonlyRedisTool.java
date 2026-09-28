package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyRedisTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyRedisTool.class);
    private static final int MAX_VALUE_BYTES = 8 * 1024;
    private final ReadonlyToolProperties properties;
    private final AuditTrail auditTrail;
    private final BindingConnectionLookup bindings;

    public ReadonlyRedisTool(
            ReadonlyToolProperties properties,
            AuditTrail auditTrail,
            BindingConnectionLookup bindings
    ) {
        this.properties = properties;
        this.auditTrail = auditTrail;
        this.bindings = bindings;
    }

    public boolean configured() {
        return !profiles().isEmpty();
    }

    public List<String> profiles() {
        if (!properties.getRedisProfiles().isEmpty()) {
            return properties.getRedisProfiles().entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                    .map(Map.Entry::getKey)
                    .toList();
        }
        return properties.getRedisUri() == null || properties.getRedisUri().isBlank()
                ? List.of()
                : List.of(properties.getDefaultProfile());
    }

    public Map<String, Object> get(String key) {
        return get(null, null, properties.getDefaultProfile(), key);
    }

    public Map<String, Object> get(String profile, String key) {
        return get(null, null, profile, key);
    }

    public Map<String, Object> get(UUID projectId, String environment, String profile, String key) {
        auditTrail.record("redis.get", key, Map.of(
                "profile", profile == null ? "" : profile,
                "target", BindingConnectionLookup.safeRedisTarget(resolveUri(projectId, environment, profile))
        ));
        return withRedis(projectId, environment, profile, commands -> {
            String value = commands.get(key);
            if (value == null) {
                Map<String, Object> missing = new LinkedHashMap<>();
                missing.put("key", key);
                missing.put("value", null);
                missing.put("truncated", false);
                return missing;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            boolean truncated = bytes.length > MAX_VALUE_BYTES;
            String visible = truncated
                    ? new String(bytes, 0, MAX_VALUE_BYTES, StandardCharsets.UTF_8)
                    : value;
            return Map.of("key", key, "value", visible, "truncated", truncated);
        });
    }

    public Map<String, Object> inspect(String key) {
        return inspect(null, null, properties.getDefaultProfile(), key);
    }

    public Map<String, Object> inspect(String profile, String key) {
        return inspect(null, null, profile, key);
    }

    public Map<String, Object> inspect(UUID projectId, String environment, String profile, String key) {
        auditTrail.record("redis.inspect", key, Map.of(
                "profile", profile == null ? "" : profile,
                "target", BindingConnectionLookup.safeRedisTarget(resolveUri(projectId, environment, profile))
        ));
        return withRedis(projectId, environment, profile, commands -> Map.of(
                "key", key,
                "type", commands.type(key),
                "ttl", commands.ttl(key)
        ));
    }

    public Map<String, Object> scanPrefix(String prefix, int limit) {
        return scanPrefix(properties.getDefaultProfile(), prefix, limit);
    }

    public Map<String, Object> scanPrefix(String profile, String prefix, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        auditTrail.record("redis.scan", prefix, Map.of("profile", profile, "limit", safeLimit));
        return withRedis(null, null, profile, commands -> {
            List<String> keys = commands.scan(
                    ScanArgs.Builder.matches(prefix + "*").limit(safeLimit)
            ).getKeys();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("prefix", prefix);
            result.put("keys", keys);
            result.put("limited", keys.size() == safeLimit);
            return result;
        });
    }

    /**
     * 采集 Redis INFO 指标与 key 总数（只读命令），供监控侧车使用。
     * 不写审计：定时采集（默认 60s 一次）写审计会刷屏。
     */
    public Map<String, Object> metricsInfo(UUID projectId, String environment, String profile) {
        return withRedis(projectId, environment, profile, commands -> {
            String info = commands.info();
            Map<String, Object> result = new LinkedHashMap<>((Integer) (info == null ? Map.of() : info));
            result.put("__dbsize", String.valueOf(commands.dbsize()));
            return result;
        });
    }

    private <T> T withRedis(
            UUID projectId,
            String environment,
            String profile,
            java.util.function.Function<RedisCommands<String, String>, T> callback
    ) {
        String uri = resolveUri(projectId, environment, profile);
        log.info("Redis 只读访问 profile={} target={}", profile, BindingConnectionLookup.safeRedisTarget(uri));
        RedisClient client = RedisClient.create(uri);
        try (var connection = client.connect()) {
            return callback.apply(connection.sync());
        } finally {
            client.shutdown();
        }
    }

    private String resolveUri(UUID projectId, String environment, String profile) {
        if (projectId != null) {
            return bindings.redisUri(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                    "当前项目环境没有已启用的 MCP Redis：" + (profile == null ? "" : profile)
                            + "。排查只使用 MCP 页保存的连接，不会回退到 application-tools.yml。"
            ));
        }
        String resolved = properties.getRedisProfiles().isEmpty()
                ? profile
                : ProfileNames.resolveExact(profile, profiles(), "Redis");
        String uri = properties.getRedisProfiles().isEmpty()
                ? properties.getRedisUri()
                : properties.getRedisProfiles().get(resolved);
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("未配置只读 Redis profile：" + profile);
        }
        return uri;
    }
}
