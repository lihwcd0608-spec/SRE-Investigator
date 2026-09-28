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
public class ReadonlyStarrocksTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyStarrocksTool.class);
    private final ReadonlyToolProperties properties;
    private final AuditTrail auditTrail;
    private final BindingConnectionLookup bindings;

    public ReadonlyStarrocksTool(
            ReadonlyToolProperties properties,
            AuditTrail auditTrail,
            BindingConnectionLookup bindings
    ) {
        this.properties = properties;
        this.auditTrail = auditTrail;
        this.bindings = bindings;
    }

    public List<Map<String, Object>> query(UUID projectId, String environment, String profile, String sql) {
        MysqlProfile endpoint = bindings.starrocks(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 StarRocks：" + (profile == null ? "" : profile)
                        + "。排查只使用工具页保存的连接。"
        ));
        String guardedSql = ReadOnlySqlGuard.normalize(sql);
        log.info(
                "StarRocks 只读查询 profile={} host={} database={} sql={}",
                profile,
                endpoint.getHost(),
                endpoint.getDatabase(),
                guardedSql
        );
        auditTrail.record("starrocks.query", profile == null ? "" : profile, Map.of(
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
}
