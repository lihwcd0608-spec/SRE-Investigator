package com.maiya.investigator.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.mcp.config.MongoEndpoint;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReadonlyMongodbTool {
    private static final Logger log = LoggerFactory.getLogger(ReadonlyMongodbTool.class);
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    static final int MAX_RESULT_CHARS = 24_000;
    static final int QUERY_MAX_TIME_SECONDS = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final BindingConnectionLookup bindings;
    private final AuditTrail auditTrail;

    public ReadonlyMongodbTool(BindingConnectionLookup bindings, AuditTrail auditTrail) {
        this.bindings = bindings;
        this.auditTrail = auditTrail;
    }

    public Map<String, Object> listDatabases(UUID projectId, String environment, String profile) {
        MongoEndpoint endpoint = require(projectId, environment, profile);
        log.info("MongoDB 列库 profile={} target={}", profile, endpoint.summary());
        auditTrail.record("mongodb.listDatabases", profile == null ? "" : profile, Map.of("target", endpoint.summary()));
        try (MongoClient client = open(endpoint)) {
            List<String> names = new ArrayList<>();
            try {
                for (String name : client.listDatabaseNames()) {
                    names.add(name);
                }
            } catch (MongoCommandException exception) {
                String fallback = endpoint.defaultDatabase();
                if (fallback == null || fallback.isBlank()) {
                    throw new IllegalStateException("没有 listDatabases 权限：" + exception.getErrorMessage());
                }
                List<String> fallbacks = new ArrayList<>();
                fallbacks.add(fallback);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("databases", fallbacks);
                result.put("count", 1);
                result.put("note", "账号不能 listDatabases，已回退到连接串里的库 " + fallback);
                return result;
            }
            return clip(baseList("databases", names));
        } catch (MongoTimeoutException exception) {
            throw new IllegalStateException("连接 MongoDB 超时：" + endpoint.summary());
        }
    }

    public Map<String, Object> listCollections(UUID projectId, String environment, String profile, String database) {
        MongoEndpoint endpoint = require(projectId, environment, profile);
        String dbName = resolveDatabase(endpoint, database);
        log.info("MongoDB 列集合 profile={} db={}", profile, dbName);
        auditTrail.record("mongodb.listCollections", profile == null ? "" : profile, Map.of("database", dbName));
        try (MongoClient client = open(endpoint)) {
            MongoDatabase db = client.getDatabase(dbName);
            List<String> names = new ArrayList<>();
            for (String name : db.listCollectionNames()) {
                names.add(name);
            }
            Map<String, Object> result = baseList("collections", names);
            result.put("database", dbName);
            return clip(result);
        } catch (MongoTimeoutException exception) {
            throw new IllegalStateException("连接 MongoDB 超时：" + endpoint.summary());
        }
    }

    public Map<String, Object> query(
            UUID projectId,
            String environment,
            String profile,
            String database,
            String collection,
            String filter,
            String projection,
            String sort,
            Integer limit
    ) {
        MongoEndpoint endpoint = require(projectId, environment, profile);
        String dbName = resolveDatabase(endpoint, database);
        String collName = MongoReadGuard.identifier(collection, "集合名");
        Document query = MongoReadGuard.normalizeQuery(MongoReadGuard.filter(filter), collName);
        Document proj = MongoReadGuard.document(projection, "projection");
        Document order = MongoReadGuard.document(sort, "sort");
        int capped = QueryTimeWindow.limit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        log.info("MongoDB 只读查询 profile={} db={} collection={} limit={}", profile, dbName, collName, capped);
        auditTrail.record("mongodb.query", profile == null ? "" : profile, Map.of(
                "database", dbName,
                "collection", collName,
                "limit", capped
        ));
        try (MongoClient client = open(endpoint)) {
            MongoCollection<Document> coll = client.getDatabase(dbName).getCollection(collName);
            FindIterable<Document> find = coll.find(query).limit(capped).maxTime(QUERY_MAX_TIME_SECONDS, TimeUnit.SECONDS);
            if (proj != null) {
                find = find.projection(proj);
            }
            if (order != null) {
                find = find.sort(order);
            }
            List<Map<String, Object>> docs = new ArrayList<>();
            for (Document item : find) {
                docs.add(toMap(item));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("database", dbName);
            result.put("collection", collName);
            result.put("count", docs.size());
            result.put("documents", docs);
            return clip(result);
        } catch (MongoTimeoutException exception) {
            throw new IllegalStateException("连接 MongoDB 超时：" + endpoint.summary());
        } catch (MongoExecutionTimeoutException | MongoCommandException exception) {
            if (isQueryTimeout(exception)) {
                throw new IllegalStateException(queryTimeoutMessage(collName, query));
            }
            throw exception;
        }
    }

    /**
     * 采集 MongoDB 监控数据（只读命令 serverStatus / dbStats / hello），供监控侧车使用。
     * 不写审计：定时采集（默认 60s 一次）写审计会刷屏。
     */
    public Map<String, Object> metricsInfo(UUID projectId, String environment, String profile) {
        MongoEndpoint endpoint = require(projectId, environment, profile);
        try (MongoClient client = open(endpoint)) {
            Document serverStatus = client.getDatabase("admin").runCommand(new Document("serverStatus", 1));
            Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
            Document dbStats = null;
            String dbName = endpoint.defaultDatabase();
            if (dbName != null && !dbName.isBlank()) {
                dbStats = client.getDatabase(dbName).runCommand(new Document("dbStats", 1).append("scale", 1048576));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("serverStatus", serverStatus);
            result.put("hello", hello);
            result.put("dbStats", dbStats);
            return result;
        }
    }

    private static boolean isQueryTimeout(Exception exception) {
        if (exception instanceof MongoExecutionTimeoutException) {
            return true;
        }
        if (exception instanceof MongoCommandException command) {
            if (command.getErrorCode() == 50) {
                return true;
            }
            String message = command.getErrorMessage();
            return message != null && message.toLowerCase(Locale.ROOT).contains("time limit");
        }
        return false;
    }

    private static String queryTimeoutMessage(String collection, Document query) {
        if (collection != null && collection.contains("user_tag_attr")) {
            return "MongoDB 查询超时：用户画像集合 " + collection + " 请用 {\"_id\": <用户id数字>} 查询，"
                    + "不要用 user_id/userId 等字段（会全表扫描）。本次条件：" + query.toJson();
        }
        return "MongoDB 查询超时：请缩小 filter、确认走了索引，或减小 limit。本次条件：" + query.toJson();
    }

    private MongoEndpoint require(UUID projectId, String environment, String profile) {
        if (projectId == null) {
            throw new IllegalStateException("MongoDB 只使用 MCP 页保存的连接，当前没有项目上下文");
        }
        return bindings.mongodb(projectId, environment, profile).orElseThrow(() -> new IllegalStateException(
                "当前项目环境没有已启用的 MCP MongoDB：" + (profile == null ? "" : profile)
                        + "。请到 MCP 页保存 MongoDB Binding 并点「测试连接」。"
        ));
    }

    private static MongoClient open(MongoEndpoint endpoint) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(endpoint.connectionUri()))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(8, TimeUnit.SECONDS))
                .applyToSocketSettings(builder -> builder.connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS))
                .build();
        return MongoClients.create(settings);
    }

    private static Map<String, Object> toMap(Document document) {
        try {
            Map<String, Object> parsed = MAPPER.readValue(document.toJson(), new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", document.toJson());
            return fallback;
        }
    }

    private static String resolveDatabase(MongoEndpoint endpoint, String database) {
        if (database != null && !database.isBlank()) {
            return MongoReadGuard.identifier(database, "库名");
        }
        String fallback = endpoint.defaultDatabase();
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalArgumentException("请指定 database，或在连接串里写默认库名");
        }
        return MongoReadGuard.identifier(fallback, "库名");
    }

    private static Map<String, Object> baseList(String key, List<String> names) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put(key, names);
        result.put("count", names.size());
        return result;
    }

    static Map<String, Object> clip(Map<String, Object> result) {
        String json = String.valueOf(result);
        if (json.length() <= MAX_RESULT_CHARS) {
            return result;
        }
        Map<String, Object> clipped = new LinkedHashMap<>(result);
        Object docs = clipped.get("documents");
        if (docs instanceof List<?> list && !list.isEmpty()) {
            int keep = Math.max(1, list.size() / 2);
            clipped.put("documents", new ArrayList<>(list.subList(0, keep)));
            clipped.put("count", keep);
        }
        clipped.put("truncated", true);
        clipped.put("note", "结果过长，已截断进 prompt");
        return clipped;
    }
}
