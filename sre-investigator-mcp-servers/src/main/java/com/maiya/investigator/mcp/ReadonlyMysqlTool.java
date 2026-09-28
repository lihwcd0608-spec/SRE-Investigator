package com.maiya.investigator.mcp;

import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties.MysqlProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyMysqlTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyMysqlTool.class);
    private final ReadonlyToolProperties properties;
    private final AuditTrail auditTrail;
    private final BindingConnectionLookup bindings;

    public ReadonlyMysqlTool(
            ReadonlyToolProperties properties,
            AuditTrail auditTrail,
            BindingConnectionLookup bindings
    ) {
        this.properties = properties;
        this.auditTrail = auditTrail;
        this.bindings = bindings;
    }

    public List<String> profiles() {
        return properties.getMysqlProfiles().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().configured())
                .map(Map.Entry::getKey)
                .toList();
    }

    public List<Map<String, Object>> query(String profile, String sql) {
        return query(null, null, profile, sql);
    }

    public List<Map<String, Object>> query(UUID projectId, String environment, String profile, String sql) {
        MysqlProfile endpoint = resolveEndpoint(projectId, environment, profile);
        String guardedSql = ReadOnlySqlGuard.normalize(sql);
        log.info(
                "MySQL 只读查询 profile={} host={} database={} sql={}",
                profile,
                endpoint.getHost(),
                endpoint.getDatabase(),
                guardedSql
        );
        auditTrail.record("mysql.query", profile == null ? "" : profile, Map.of(
                "sql", guardedSql,
                "host", endpoint.getHost(),
                "database", endpoint.getDatabase()
        ));
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(endpoint.jdbcUrl());
        dataSource.setUsername(endpoint.getUsername());
        dataSource.setPassword(endpoint.getPassword());
        return JdbcClient.create(dataSource)
                .sql(guardedSql)
                .query()
                .listOfRows()
                .stream()
                .limit(properties.getMaxRows())
                .toList();
    }

    public List<Map<String, Object>> schema(String profile, String table) {
        if (!table.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("表名只能包含字母、数字和下划线");
        }
        return query(profile, "DESCRIBE `" + table + "`");
    }

    private MysqlProfile resolveEndpoint(UUID projectId, String environment, String profile) {
        if (projectId != null) {
            return bindings.mysql(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                    "当前项目环境没有已启用的 MCP MySQL：" + (profile == null ? "" : profile)
                            + "。排查只使用 MCP 页保存的连接，不会回退到 application-tools.yml。"
            ));
        }
        return yamlProfile(profile);
    }

    private MysqlProfile yamlProfile(String profile) {
        String resolved = ProfileNames.resolveExact(profile, profiles(), "MySQL");
        MysqlProfile endpoint = properties.getMysqlProfiles().get(resolved);
        if (endpoint == null || !endpoint.configured()) {
            throw new IllegalArgumentException("未配置只读 MySQL profile：" + profile + "；可用：" + String.join(", ", profiles()));
        }
        return endpoint;
    }
}
