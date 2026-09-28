package com.maiya.investigator.mcp;

import com.maiya.investigator.api.McpConnectionTestRequest;
import com.maiya.investigator.api.McpConnectionTestResponse;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.mcp.aliyun.AliyunCloudClient;
import com.maiya.investigator.mcp.aliyun.MseRpcClient;
import com.maiya.investigator.mcp.aliyun.SlsLogClient;
import com.maiya.investigator.mcp.config.ArmsEndpoint;
import com.maiya.investigator.mcp.config.CdnEndpoint;
import com.maiya.investigator.mcp.config.LoadBalancerEndpoint;
import com.maiya.investigator.mcp.config.KafkaEndpoint;
import com.maiya.investigator.mcp.config.KuboardEndpoint;
import com.maiya.investigator.mcp.config.MseEndpoint;
import com.maiya.investigator.mcp.config.RocketMqEndpoint;
import com.maiya.investigator.mcp.config.SlsEndpoint;
import com.maiya.investigator.mcp.mq.KafkaReadonlyClient;
import com.maiya.investigator.mcp.mq.RocketMqReadonlyClient;
import com.maiya.investigator.mcp.k8s.KuboardClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.stereotype.Service;

@Service
public class McpConnectionTester {
    private static final int TIMEOUT_MS = 5000;
    private final SlsLogClient sls;
    private final MseRpcClient mse;
    private final KuboardClient k8s;
    private final KafkaReadonlyClient kafka;
    private final RocketMqReadonlyClient rocketmq;
    private final AliyunCloudClient aliyun;

    public McpConnectionTester(
            SlsLogClient sls,
            MseRpcClient mse,
            KuboardClient k8s,
            KafkaReadonlyClient kafka,
            RocketMqReadonlyClient rocketmq,
            AliyunCloudClient aliyun
    ) {
        this.sls = sls;
        this.mse = mse;
        this.k8s = k8s;
        this.kafka = kafka;
        this.rocketmq = rocketmq;
        this.aliyun = aliyun;
    }

    public McpConnectionTestResponse test(McpConnectionTestRequest request) {
        ToolKind kind;
        try {
            kind = ToolKind.valueOf(request.toolKind().trim().toUpperCase());
        } catch (RuntimeException exception) {
            return McpConnectionTestResponse.failure("不支持的工具类型：" + request.toolKind());
        }
        return switch (kind) {
            case MYSQL -> testMysql(request);
            case REDIS -> testRedis(request);
            case MONGODB -> testMongo(request);
            case ELASTICSEARCH -> testElasticsearch(request);
            case CODE -> testCode(request);
            case LOGS -> testLogs(request);
            case MSE -> testMse(request);
            case ARMS -> testArms(request);
            case LOAD_BALANCER -> testLoadBalancer(request);
            case CDN -> testCdn(request);
            case K8S -> testK8s(request);
            case STARROCKS -> testStarrocks(request);
            case KAFKA -> testKafka(request);
            case ROCKETMQ -> testRocketMq(request);
            case MCP_SERVER -> McpConnectionTestResponse.success(
                    "开源 MCP 已记录启动命令。当前排查不会自动拉起外部进程，保存后即可在工具页管理。"
            );
        };
    }

    private McpConnectionTestResponse testMysql(McpConnectionTestRequest request) {
        if (blank(request.host()) || blank(request.database()) || blank(request.username())) {
            return McpConnectionTestResponse.failure("MySQL 需要填写主机、数据库和用户名");
        }
        int port = request.port() == null || request.port() <= 0 ? 3306 : request.port();
        String params = blank(request.parameters())
                ? "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                : request.parameters();
        String url = "jdbc:mysql://" + request.host().trim() + ":" + port + "/" + request.database().trim()
                + "?" + params + "&connectTimeout=" + TIMEOUT_MS + "&socketTimeout=" + TIMEOUT_MS;
        Properties properties = new Properties();
        properties.setProperty("user", request.username());
        properties.setProperty("password", request.password() == null ? "" : request.password());
        try (Connection connection = DriverManager.getConnection(url, properties);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            if (!resultSet.next()) {
                return McpConnectionTestResponse.failure("已连上但 SELECT 1 没有返回结果");
            }
            return McpConnectionTestResponse.success("MySQL 连通，只读探测 SELECT 1 成功");
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testRedis(McpConnectionTestRequest request) {
        try {
            RedisURI uri = redisUri(request);
            RedisClient client = RedisClient.create(uri);
            try (var connection = client.connect()) {
                String pong = connection.sync().ping();
                if (!"PONG".equalsIgnoreCase(pong)) {
                    return McpConnectionTestResponse.failure("已连上但 PING 返回：" + pong);
                }
                return McpConnectionTestResponse.success("Redis 连通，PING 成功");
            } finally {
                client.shutdown();
            }
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private RedisURI redisUri(McpConnectionTestRequest request) {
        if (!blank(request.uri())) {
            return RedisURI.create(request.uri().trim());
        }
        if (blank(request.host())) {
            throw new IllegalArgumentException("Redis 需要填写主机，或直接填 redis:// URI");
        }
        int port = request.port() == null || request.port() <= 0 ? 6379 : request.port();
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(request.host().trim())
                .withPort(port)
                .withTimeout(Duration.ofMillis(TIMEOUT_MS));
        if (!blank(request.password())) {
            builder.withPassword(request.password().toCharArray());
        }
        if (!blank(request.database()) && request.database().matches("\\d+")) {
            builder.withDatabase(Integer.parseInt(request.database()));
        }
        return builder.build();
    }

    private McpConnectionTestResponse testMongo(McpConnectionTestRequest request) {
        if (!blank(request.uri()) && request.uri().trim().toLowerCase().startsWith("mongodb+srv://")) {
            return McpConnectionTestResponse.failure("mongodb+srv 已可保存，连通性检查请改用主机+端口或 mongodb:// URI");
        }
        HostPort target = mongoTarget(request);
        if (target == null) {
            return McpConnectionTestResponse.failure("MongoDB 需要填写主机，或直接填 mongodb:// URI");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host(), target.port()), TIMEOUT_MS);
            return McpConnectionTestResponse.success("MongoDB 端口可达：" + target.host() + ":" + target.port());
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testElasticsearch(McpConnectionTestRequest request) {
        String url = elasticsearchUrl(request);
        if (url == null) {
            return McpConnectionTestResponse.failure("Elasticsearch 需要填写主机，或直接填 http(s):// URI");
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .GET();
            if (!blank(request.username())) {
                String token = Base64.getEncoder().encodeToString(
                        (request.username() + ":" + (request.password() == null ? "" : request.password()))
                                .getBytes(StandardCharsets.UTF_8)
                );
                builder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return McpConnectionTestResponse.success("Elasticsearch 连通，HTTP " + status);
            }
            return McpConnectionTestResponse.failure("已连上但 HTTP " + status);
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private static HostPort mongoTarget(McpConnectionTestRequest request) {
        if (!blank(request.uri())) {
            String rest = request.uri().trim().replaceFirst("(?i)^mongodb://", "");
            int at = rest.lastIndexOf('@');
            if (at >= 0) {
                rest = rest.substring(at + 1);
            }
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                rest = rest.substring(0, slash);
            }
            int colon = rest.lastIndexOf(':');
            if (colon > 0) {
                try {
                    return new HostPort(rest.substring(0, colon), Integer.parseInt(rest.substring(colon + 1)));
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
            return rest.isBlank() ? null : new HostPort(rest, 27017);
        }
        if (blank(request.host())) {
            return null;
        }
        int port = request.port() == null || request.port() <= 0 ? 27017 : request.port();
        return new HostPort(request.host().trim(), port);
    }

    private static String elasticsearchUrl(McpConnectionTestRequest request) {
        if (!blank(request.uri())) {
            String uri = request.uri().trim();
            return uri.endsWith("/") ? uri : uri + "/";
        }
        if (blank(request.host())) {
            return null;
        }
        int port = request.port() == null || request.port() <= 0 ? 9200 : request.port();
        return "http://" + request.host().trim() + ":" + port + "/";
    }

    private McpConnectionTestResponse testLogs(McpConnectionTestRequest request) {
        SlsEndpoint endpoint = BindingConnectionLookup.slsEndpoint(configFrom(request)).orElse(null);
        if (endpoint == null) {
            return McpConnectionTestResponse.failure("SLS 需要填写 regionId、project、AccessKeyId 和 AccessKeySecret");
        }
        try {
            Map<String, Object> listed = sls.listLogstores(endpoint);
            Object count = listed.getOrDefault("total", listed.getOrDefault("count", ""));
            return McpConnectionTestResponse.success(
                    "SLS 连通，project=" + endpoint.project() + "，ListLogStores 成功" + (count == null || count.toString().isBlank() ? "" : "（" + count + "）")
            );
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testMse(McpConnectionTestRequest request) {
        MseEndpoint endpoint = BindingConnectionLookup.mseEndpoint(configFrom(request)).orElse(null);
        if (endpoint == null) {
            return McpConnectionTestResponse.failure("MSE 需要填写 regionId、AccessKeyId 和 AccessKeySecret");
        }
        try {
            mse.call(endpoint, "ListGateway", Map.of("PageNumber", "1", "PageSize", "1"));
            return McpConnectionTestResponse.success("MSE 连通，region=" + endpoint.regionId() + "，ListGateway 成功");
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testArms(McpConnectionTestRequest request) {
        if (!aliyun.configured()) {
            return McpConnectionTestResponse.failure(
                    "未配置全局阿里云 RAM 只读 AK：ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET"
            );
        }
        ArmsEndpoint endpoint = BindingConnectionLookup.armsEndpoint(configFrom(request)).orElse(null);
        if (endpoint == null) {
            return McpConnectionTestResponse.failure("ARMS 需要填写地域 regionId");
        }
        try {
            aliyun.arms(endpoint.regionId(), "ListTraceApps", Map.of("AppType", "TRACE"));
            String scope = endpoint.applications().isEmpty()
                    ? "未限制应用"
                    : "已开放 " + endpoint.applications().size() + " 个应用";
            return McpConnectionTestResponse.success("ARMS 连通，region=" + endpoint.regionId() + "，ListTraceApps 成功（" + scope + "）");
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testLoadBalancer(McpConnectionTestRequest request) {
        if (!aliyun.configured()) {
            return McpConnectionTestResponse.failure(
                    "未配置全局阿里云 RAM 只读 AK：ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET"
            );
        }
        LoadBalancerEndpoint endpoint = BindingConnectionLookup.loadBalancerEndpoint(configFrom(request)).orElse(null);
        if (endpoint == null) {
            return McpConnectionTestResponse.failure("负载均衡需要填写地域 regionId，类型为 ALB 或 CLB");
        }
        try {
            if ("ALB".equals(endpoint.serviceType())) {
                aliyun.alb(endpoint.regionId(), "ListLoadBalancers", Map.of("MaxResults", "1"));
            } else {
                aliyun.clb(endpoint.regionId(), "DescribeLoadBalancers", Map.of("PageNumber", "1", "PageSize", "1"));
            }
            String scope = endpoint.instanceIds().isEmpty()
                    ? "未限制实例"
                    : "已开放 " + endpoint.instanceIds().size() + " 个实例";
            return McpConnectionTestResponse.success(
                    endpoint.serviceType() + " 连通，region=" + endpoint.regionId() + "（" + scope + "）"
            );
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testCdn(McpConnectionTestRequest request) {
        if (!aliyun.configured()) {
            return McpConnectionTestResponse.failure(
                    "未配置全局阿里云 RAM 只读 AK：ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET"
            );
        }
        CdnEndpoint endpoint = BindingConnectionLookup.cdnEndpoint(configFrom(request))
                .orElseGet(() -> new CdnEndpoint(new ArrayList<>()));
        try {
            if (endpoint.domains().isEmpty()) {
                aliyun.cdn("DescribeUserDomains", Map.of("PageNumber", "1", "PageSize", "1"));
                return McpConnectionTestResponse.success("CDN 连通，DescribeUserDomains 成功（未限制域名）");
            }
            String first = endpoint.domains().get(0);
            aliyun.cdn("DescribeCdnDomainDetail", Map.of("DomainName", first));
            return McpConnectionTestResponse.success(
                    "CDN 连通，已校验域名 " + first + "（共开放 " + endpoint.domains().size() + " 个）"
            );
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testK8s(McpConnectionTestRequest request) {
        KuboardEndpoint creds = BindingConnectionLookup.k8sCredentials(configFrom(request)).orElse(null);
        if (creds == null) {
            return McpConnectionTestResponse.failure("K8s 需要填写 Kuboard 地址、用户名和密码");
        }
        try {
            k8s.login(creds);
            List<String> names = new ArrayList<>();
            try {
                names.addAll(k8s.listClusterNames(creds));
            } catch (Exception ignored) {
                // 登录成功但列集群失败时，若已填集群名仍探测 namespaces
            }
            String wanted = request.cluster() == null ? "" : request.cluster().trim();
            if (wanted.isBlank()) {
                if (names.isEmpty()) {
                    return McpConnectionTestResponse.success(
                            "Kuboard 已登录，但没有列出集群。请手动填写集群名后保存。",
                            names
                    );
                }
                return McpConnectionTestResponse.success(
                        "Kuboard 已登录，发现 " + names.size() + " 个集群。请勾选要开放的集群后保存。",
                        names
                );
            }
            if (!names.isEmpty() && !names.contains(wanted)) {
                return McpConnectionTestResponse.failure(
                        "集群「" + wanted + "」在 Kuboard 里不存在。现网集群名：" + String.join("、", names),
                        names
                );
            }
            KuboardEndpoint endpoint = creds.withCluster(wanted);
            k8s.getJson(endpoint, "/api/v1/namespaces");
            String extra = names.isEmpty() ? "" : "。现网共 " + names.size() + " 个集群，可勾选开放";
            return McpConnectionTestResponse.success(
                    "Kuboard 连通，cluster=" + wanted + "，已列出命名空间" + extra,
                    names
            );
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.contains("未找到集群")) {
                try {
                    List<String> names = k8s.listClusterNames(creds);
                    if (!names.isEmpty()) {
                        return McpConnectionTestResponse.failure(
                                "集群「" + creds.cluster() + "」在 Kuboard 里不存在。现网集群名："
                                        + String.join("、", names),
                                names
                        );
                    }
                } catch (Exception ignored) {
                    // keep original error
                }
            }
            return McpConnectionTestResponse.failure(explainK8sFailure(exception));
        }
    }

    private static String explainK8sFailure(Exception exception) {
        String message = safe(exception);
        String lower = message.toLowerCase();
        if (message.contains("K8s 无权限") || lower.contains("forbidden") || lower.contains("cannot list")) {
            return message;
        }
        if (message.contains("用户名") || message.contains("密码") || message.contains("登录")
                || lower.contains("unauthorized") || message.contains("401")
                || (message.contains("403") && !lower.contains("forbidden"))) {
            return "账密或 SSO 失败：" + message;
        }
        if (message.contains("404") || lower.contains("cluster") || message.contains("集群")) {
            return "集群名或代理路径无效：" + message;
        }
        if (lower.contains("unknownhost") || lower.contains("connection")
                || message.contains("无法访问") || message.contains("调用 Kuboard")) {
            return "无法访问 Kuboard 地址：" + message;
        }
        return message;
    }

    private McpConnectionTestResponse testStarrocks(McpConnectionTestRequest request) {
        if (blank(request.host()) || blank(request.database()) || blank(request.username())) {
            return McpConnectionTestResponse.failure("StarRocks 需要填写主机、数据库和用户名");
        }
        int port = request.port() == null || request.port() <= 0 ? 9030 : request.port();
        String params = blank(request.parameters())
                ? "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                : request.parameters();
        String url = "jdbc:mysql://" + request.host().trim() + ":" + port + "/" + request.database().trim()
                + "?" + params + "&connectTimeout=" + TIMEOUT_MS + "&socketTimeout=" + TIMEOUT_MS;
        Properties properties = new Properties();
        properties.setProperty("user", request.username());
        properties.setProperty("password", request.password() == null ? "" : request.password());
        try (Connection connection = DriverManager.getConnection(url, properties);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            if (!resultSet.next()) {
                return McpConnectionTestResponse.failure("已连上但 SELECT 1 没有返回结果");
            }
            return McpConnectionTestResponse.success("StarRocks 连通，只读探测 SELECT 1 成功");
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testKafka(McpConnectionTestRequest request) {
        KafkaEndpoint endpoint = BindingConnectionLookup.kafkaEndpoint(configFrom(request)).orElse(null);
        if (endpoint == null) {
            return McpConnectionTestResponse.failure("Kafka 需要填写 Bootstrap Servers");
        }
        try {
            Map<String, Object> listed = kafka.listTopics(endpoint);
            Object count = listed.get("count");
            return McpConnectionTestResponse.success("Kafka 连通，已列出 " + count + " 个 topic");
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private McpConnectionTestResponse testRocketMq(McpConnectionTestRequest request) {
        RocketMqEndpoint endpoint = BindingConnectionLookup.rocketmqEndpoint(configFrom(request)).orElse(null);
        if (endpoint == null) {
            return McpConnectionTestResponse.failure("RocketMQ 需要填写 NameServer 地址");
        }
        try {
            Map<String, Object> listed = rocketmq.listTopics(endpoint);
            Object count = listed.get("count");
            return McpConnectionTestResponse.success("RocketMQ 连通，已列出 " + count + " 个 topic");
        } catch (Exception exception) {
            return McpConnectionTestResponse.failure(safe(exception));
        }
    }

    private static Map<String, Object> configFrom(McpConnectionTestRequest request) {
        Map<String, Object> config = new LinkedHashMap<>();
        put(config, "host", request.host());
        put(config, "database", request.database());
        put(config, "username", request.username());
        put(config, "password", request.password());
        put(config, "parameters", request.parameters());
        put(config, "uri", request.uri());
        put(config, "regionId", request.regionId());
        put(config, "project", request.project());
        put(config, "logstore", request.logstore());
        put(config, "accessKeyId", request.accessKeyId());
        put(config, "accessKeySecret", request.accessKeySecret());
        put(config, "endpoint", request.endpoint());
        put(config, "gatewayUniqueId", request.gatewayUniqueId());
        put(config, "nacosInstanceId", request.nacosInstanceId());
        put(config, "username", request.username());
        put(config, "password", request.password());
        String baseUrl = request.baseUrl() != null && !request.baseUrl().isBlank() ? request.baseUrl() : request.uri();
        put(config, "baseUrl", baseUrl);
        put(config, "cluster", request.cluster());
        put(config, "namespace", request.namespace());
        if (request.insecureSkipTls() != null) {
            config.put("insecureSkipTls", request.insecureSkipTls());
        }
        put(config, "serviceType", request.serviceType());
        put(config, "applications", request.applications());
        put(config, "instanceIds", request.instanceIds());
        put(config, "domains", request.domains());
        return config;
    }

    private static void put(Map<String, Object> config, String key, String value) {
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }

    private McpConnectionTestResponse testCode(McpConnectionTestRequest request) {
        if (blank(request.rootPath())) {
            return McpConnectionTestResponse.failure("代码仓需要填写绝对路径");
        }
        Path path = Path.of(request.rootPath().trim());
        if (!path.isAbsolute()) {
            return McpConnectionTestResponse.failure("代码仓路径必须是绝对路径");
        }
        if (!Files.isDirectory(path)) {
            return McpConnectionTestResponse.failure("路径不存在或不是目录：" + path);
        }
        return McpConnectionTestResponse.success("本地目录可读：" + path);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replaceAll("(?i)(password|passwd)=[^\\s,;]+", "$1=***");
    }

    private record HostPort(String host, int port) {
    }
}
