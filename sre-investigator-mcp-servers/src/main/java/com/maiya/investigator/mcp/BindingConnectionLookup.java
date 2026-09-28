package com.maiya.investigator.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.infra.security.SecretCipher;
import com.maiya.investigator.mcp.config.ArmsEndpoint;
import com.maiya.investigator.mcp.config.CdnEndpoint;
import com.maiya.investigator.mcp.config.KafkaEndpoint;
import com.maiya.investigator.mcp.config.KuboardEndpoint;
import com.maiya.investigator.mcp.config.LoadBalancerEndpoint;
import com.maiya.investigator.mcp.config.MongoEndpoint;
import com.maiya.investigator.mcp.config.MseEndpoint;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties.MysqlProfile;
import com.maiya.investigator.mcp.config.RocketMqEndpoint;
import com.maiya.investigator.mcp.config.SlsEndpoint;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BindingConnectionLookup {
    private final ControlPlaneStore controlPlane;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;

    public BindingConnectionLookup(ControlPlaneStore controlPlane, SecretCipher cipher, ObjectMapper mapper) {
        this.controlPlane = controlPlane;
        this.cipher = cipher;
        this.mapper = mapper;
    }

    public Optional<MysqlProfile> mysql(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.MYSQL, profile).map(this::requireMysql);
    }

    public Optional<String> redisUri(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.REDIS, profile).map(this::requireRedis);
    }

    public Optional<EsEndpoint> elasticsearch(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.ELASTICSEARCH, profile).map(this::requireEs);
    }

    private EsEndpoint requireEs(ToolBinding binding) {
        Map<String, Object> config = decrypt(binding.id());
        String uri = text(config.get("uri"));
        String username = text(config.get("username"));
        String password = text(config.get("password"));
        if (!uri.isBlank()) {
            return new EsEndpoint(uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri, username, password);
        }
        String host = text(config.get("host"));
        if (host.isBlank()) {
            throw new IllegalStateException("Elasticsearch 绑定缺少主机配置：" + binding.profile());
        }
        int port = integer(config.get("port"), 9200);
        return new EsEndpoint("http://" + host + ":" + port, username, password);
    }

    /** Elasticsearch 监控访问端点（只读 REST）。 */
    public record EsEndpoint(String baseUrl, String username, String password) {
    }

    public Optional<SlsEndpoint> sls(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.LOGS, profile).map(this::requireSls);
    }

    public Optional<MseEndpoint> mse(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.MSE, profile).map(this::requireMse);
    }

    public Optional<ArmsEndpoint> arms(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.ARMS, profile).map(this::requireArms);
    }

    public Optional<LoadBalancerEndpoint> loadBalancer(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.LOAD_BALANCER, profile).map(this::requireLoadBalancer);
    }

    public Optional<CdnEndpoint> cdn(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.CDN, profile).map(this::requireCdn);
    }

    public Optional<KuboardEndpoint> k8s(UUID projectId, String environment, String profile) {
        return k8s(projectId, environment, profile, null);
    }

    public Optional<KuboardEndpoint> k8s(UUID projectId, String environment, String profile, String cluster) {
        return binding(projectId, environment, ToolKind.K8S, profile).map(item -> requireK8s(item, cluster));
    }

    public List<String> k8sAllowedClusters(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.K8S, profile)
                .map(item -> parseClusterNames(decrypt(item.id())))
                .orElseGet(ArrayList::new);
    }

    public Optional<MysqlProfile> starrocks(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.STARROCKS, profile).map(this::requireStarrocks);
    }

    public Optional<KafkaEndpoint> kafka(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.KAFKA, profile).map(this::requireKafka);
    }

    public Optional<RocketMqEndpoint> rocketmq(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.ROCKETMQ, profile).map(this::requireRocketMq);
    }

    public Optional<MongoEndpoint> mongodb(UUID projectId, String environment, String profile) {
        return binding(projectId, environment, ToolKind.MONGODB, profile).map(this::requireMongodb);
    }

    public Optional<String> mysqlSummary(UUID projectId, String environment, String profile) {
        return mysql(projectId, environment, profile)
                .map(item -> item.getHost() + "/" + item.getDatabase());
    }

    public Optional<String> redisSummary(UUID projectId, String environment, String profile) {
        return redisUri(projectId, environment, profile).map(BindingConnectionLookup::safeRedisTarget);
    }

    public Optional<String> slsSummary(UUID projectId, String environment, String profile) {
        return sls(projectId, environment, profile).map(SlsEndpoint::summary);
    }

    public Optional<String> mseSummary(UUID projectId, String environment, String profile) {
        return mse(projectId, environment, profile).map(MseEndpoint::summary);
    }

    public Optional<String> armsSummary(UUID projectId, String environment, String profile) {
        return arms(projectId, environment, profile).map(ArmsEndpoint::summary);
    }

    public Optional<String> loadBalancerSummary(UUID projectId, String environment, String profile) {
        return loadBalancer(projectId, environment, profile).map(LoadBalancerEndpoint::summary);
    }

    public Optional<String> cdnSummary(UUID projectId, String environment, String profile) {
        return cdn(projectId, environment, profile).map(CdnEndpoint::summary);
    }

    public Optional<String> k8sSummary(UUID projectId, String environment, String profile) {
        return k8s(projectId, environment, profile).map(KuboardEndpoint::summary);
    }

    public Optional<String> starrocksSummary(UUID projectId, String environment, String profile) {
        return starrocks(projectId, environment, profile)
                .map(item -> item.getHost() + "/" + item.getDatabase());
    }

    public Optional<String> kafkaSummary(UUID projectId, String environment, String profile) {
        return kafka(projectId, environment, profile).map(KafkaEndpoint::summary);
    }

    public Optional<String> rocketmqSummary(UUID projectId, String environment, String profile) {
        return rocketmq(projectId, environment, profile).map(RocketMqEndpoint::summary);
    }

    public Optional<String> mongodbSummary(UUID projectId, String environment, String profile) {
        return mongodb(projectId, environment, profile).map(MongoEndpoint::summary);
    }

    private Optional<ToolBinding> binding(UUID projectId, String environment, ToolKind kind, String profile) {
        if (projectId == null) {
            return Optional.empty();
        }
        Environment env = parseEnvironment(environment);
        List<ToolBinding> matches = controlPlane.bindings(projectId).stream()
                .filter(ToolBinding::enabled)
                .filter(item -> item.toolKind() == kind)
                .filter(item -> env == null || item.environment() == env)
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = matches.stream().map(ToolBinding::profile).toList();
        try {
            String resolved = ProfileNames.resolve(profile, names, kind.name());
            return matches.stream().filter(item -> item.profile().equals(resolved)).findFirst();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private MysqlProfile requireMysql(ToolBinding binding) {
        return mysqlProfile(binding).orElseThrow(() -> new IllegalStateException(
                "MCP MySQL「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到 MCP 页重新保存并点「测试连接」。"
        ));
    }

    private String requireRedis(ToolBinding binding) {
        return redisEndpoint(binding).orElseThrow(() -> new IllegalStateException(
                "MCP Redis「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到 MCP 页重新保存并点「测试连接」。"
        ));
    }

    private SlsEndpoint requireSls(ToolBinding binding) {
        return slsEndpoint(decrypt(binding.id())).orElseThrow(() -> new IllegalStateException(
                "MCP 日志「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到 MCP 页重新保存并点「测试连接」。"
        ));
    }

    private MseEndpoint requireMse(ToolBinding binding) {
        return mseEndpoint(decrypt(binding.id())).orElseThrow(() -> new IllegalStateException(
                "MCP MSE「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到 MCP 页重新保存并点「测试连接」。"
        ));
    }

    private ArmsEndpoint requireArms(ToolBinding binding) {
        return armsEndpoint(decrypt(binding.id())).orElseThrow(() -> invalidCloudBinding("ARMS", binding));
    }

    private LoadBalancerEndpoint requireLoadBalancer(ToolBinding binding) {
        return loadBalancerEndpoint(decrypt(binding.id()))
                .orElseThrow(() -> invalidCloudBinding("负载均衡", binding));
    }

    private CdnEndpoint requireCdn(ToolBinding binding) {
        return cdnEndpoint(decrypt(binding.id())).orElseThrow(() -> invalidCloudBinding("CDN", binding));
    }

    private static IllegalStateException invalidCloudBinding(String kind, ToolBinding binding) {
        return new IllegalStateException(
                kind + "「" + binding.profile() + "」已启用，但资源范围配置无效。请到工具页重新保存并测试。"
        );
    }

    private KuboardEndpoint requireK8s(ToolBinding binding, String cluster) {
        Map<String, Object> config = decrypt(binding.id());
        KuboardEndpoint endpoint = k8sEndpoint(config).orElseThrow(() -> new IllegalStateException(
                "MCP K8s「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到 MCP 页重新保存并点「测试连接」。"
        ));
        return endpoint.withCluster(resolveCluster(config, cluster));
    }

    private MysqlProfile requireStarrocks(ToolBinding binding) {
        return starrocksProfile(decrypt(binding.id())).orElseThrow(() -> new IllegalStateException(
                "StarRocks「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到工具页重新保存并点「测试连接」。"
        ));
    }

    private KafkaEndpoint requireKafka(ToolBinding binding) {
        return kafkaEndpoint(decrypt(binding.id())).orElseThrow(() -> new IllegalStateException(
                "Kafka「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到工具页重新保存并点「测试连接」。"
        ));
    }

    private RocketMqEndpoint requireRocketMq(ToolBinding binding) {
        return rocketmqEndpoint(decrypt(binding.id())).orElseThrow(() -> new IllegalStateException(
                "RocketMQ「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到工具页重新保存并点「测试连接」。"
        ));
    }

    private MongoEndpoint requireMongodb(ToolBinding binding) {
        return mongoEndpoint(decrypt(binding.id())).orElseThrow(() -> new IllegalStateException(
                "MongoDB「" + binding.profile() + "」已启用，但连接配置无效或无法解密。请到 MCP 页重新保存并点「测试连接」。"
        ));
    }

    static Optional<MongoEndpoint> mongoEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        MongoEndpoint endpoint = new MongoEndpoint(
                text(config.get("uri")),
                text(config.get("host")),
                integer(config.get("port"), 27017),
                text(config.get("database")),
                text(config.get("username")),
                text(config.get("password"))
        );
        return endpoint.configured() ? Optional.of(endpoint) : Optional.empty();
    }

    static Optional<KuboardEndpoint> k8sCredentials(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = parseClusterNames(config);
        String cluster = names.isEmpty() ? text(first(config, "cluster", "clusterName")) : names.get(0);
        KuboardEndpoint endpoint = new KuboardEndpoint(
                text(first(config, "baseUrl", "uri")),
                text(config.get("username")),
                text(config.get("password")),
                cluster,
                text(first(config, "namespace", "defaultNamespace")),
                bool(config.get("insecureSkipTls"))
        );
        return endpoint.credentialsReady() ? Optional.of(endpoint) : Optional.empty();
    }

    static Optional<KuboardEndpoint> k8sEndpoint(Map<String, Object> config) {
        return k8sCredentials(config).filter(KuboardEndpoint::configured);
    }

    static String resolveCluster(Map<String, Object> config, String requested) {
        List<String> allowed = parseClusterNames(config);
        if (allowed.isEmpty()) {
            throw new IllegalStateException("这个 K8s Binding 没有开放任何集群。请到 MCP 页勾选集群后保存。");
        }
        String wanted = requested == null || requested.isBlank() ? allowed.get(0) : requested.trim();
        if (!allowed.contains(wanted)) {
            throw new IllegalArgumentException(
                    "这个 K8s Binding 未开放集群 " + wanted + "。可用：" + String.join(" / ", allowed)
            );
        }
        return wanted;
    }

    static List<String> parseClusterNames(Map<String, Object> config) {
        List<String> names = new ArrayList<>();
        if (config == null || config.isEmpty()) {
            return names;
        }
        addClusterNames(names, config.get("clusters"));
        String fallback = text(first(config, "cluster", "clusterName"));
        if (!fallback.isBlank()) {
            names.remove(fallback);
            names.add(0, fallback);
        }
        return names;
    }

    private static void addClusterNames(List<String> names, Object raw) {
        if (raw == null) {
            return;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addClusterNames(names, item);
            }
            return;
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank() || "null".equals(text)) {
            return;
        }
        for (String part : text.split("[,，]+")) {
            String name = part.trim();
            if (!name.isBlank() && !names.contains(name)) {
                names.add(name);
            }
        }
    }

    static Optional<SlsEndpoint> slsEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        SlsEndpoint endpoint = new SlsEndpoint(
                text(config.get("regionId")),
                text(config.get("project")),
                SlsEndpoint.parseLogstores(first(config, "logstore", "logstores")),
                text(first(config, "accessKeyId", "accessKey")),
                text(config.get("accessKeySecret")),
                text(config.get("endpoint"))
        );
        return endpoint.configured() ? Optional.of(endpoint) : Optional.empty();
    }

    static Optional<MseEndpoint> mseEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        MseEndpoint endpoint = new MseEndpoint(
                text(config.get("regionId")),
                text(first(config, "accessKeyId", "accessKey")),
                text(config.get("accessKeySecret")),
                text(config.get("gatewayUniqueId")),
                text(config.get("nacosInstanceId")),
                text(config.get("endpoint"))
        );
        return endpoint.configured() ? Optional.of(endpoint) : Optional.empty();
    }

    static Optional<ArmsEndpoint> armsEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        ArmsEndpoint endpoint = new ArmsEndpoint(
                text(config.get("regionId")),
                stringList(first(config, "applications", "applicationNames"))
        );
        return endpoint.configured() ? Optional.of(endpoint) : Optional.empty();
    }

    static Optional<LoadBalancerEndpoint> loadBalancerEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        try {
            LoadBalancerEndpoint endpoint = new LoadBalancerEndpoint(
                    text(config.get("regionId")),
                    text(first(config, "serviceType", "loadBalancerType")),
                    stringList(first(config, "instanceIds", "resources"))
            );
            return endpoint.configured() ? Optional.of(endpoint) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static Optional<CdnEndpoint> cdnEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CdnEndpoint(stringList(first(config, "domains", "domainNames"))));
    }

    static Optional<MysqlProfile> starrocksProfile(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        MysqlProfile profile = new MysqlProfile();
        profile.setHost(text(config.get("host")));
        profile.setPort(integer(config.get("port"), 9030));
        profile.setDatabase(text(config.get("database")));
        profile.setUsername(text(config.get("username")));
        profile.setPassword(text(config.get("password")));
        profile.setParameters(text(config.get("parameters")));
        return profile.configured() ? Optional.of(profile) : Optional.empty();
    }

    static Optional<KafkaEndpoint> kafkaEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        KafkaEndpoint endpoint = new KafkaEndpoint(
                text(first(config, "bootstrapServers", "host", "uri")),
                text(first(config, "securityProtocol", "parameters")),
                text(first(config, "saslMechanism", "namespace")),
                text(config.get("username")),
                text(config.get("password")),
                text(first(config, "consumerGroup", "cluster"))
        );
        return endpoint.configured() ? Optional.of(endpoint) : Optional.empty();
    }

    static Optional<RocketMqEndpoint> rocketmqEndpoint(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        RocketMqEndpoint endpoint = new RocketMqEndpoint(
                text(first(config, "namesrvAddr", "host", "endpoint")),
                text(first(config, "accessKeyId", "accessKey", "username")),
                text(first(config, "accessKeySecret", "secretKey", "password")),
                text(config.get("cluster")),
                text(config.get("namespace"))
        );
        return endpoint.configured() ? Optional.of(endpoint) : Optional.empty();
    }

    private static Object first(Map<String, Object> config, String... keys) {
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        addClusterNames(values, raw);
        return values;
    }

    private Optional<MysqlProfile> mysqlProfile(ToolBinding binding) {
        Map<String, Object> config = decrypt(binding.id());
        if (config.isEmpty()) {
            return Optional.empty();
        }
        MysqlProfile profile = new MysqlProfile();
        profile.setHost(text(config.get("host")));
        profile.setPort(integer(config.get("port"), 3306));
        profile.setDatabase(text(config.get("database")));
        profile.setUsername(text(config.get("username")));
        profile.setPassword(text(config.get("password")));
        profile.setParameters(text(config.get("parameters")));
        return profile.configured() ? Optional.of(profile) : Optional.empty();
    }

    private Optional<String> redisEndpoint(ToolBinding binding) {
        Map<String, Object> config = decrypt(binding.id());
        String uri = text(config.get("uri"));
        if (!uri.isBlank()) {
            return Optional.of(uri);
        }
        String host = text(config.get("host"));
        if (host.isBlank()) {
            return Optional.empty();
        }
        int port = integer(config.get("port"), 6379);
        String database = text(config.get("database"));
        String password = text(config.get("password"));
        String auth = password.isBlank() ? "" : ":" + encodeUserinfo(password) + "@";
        String db = database.isBlank() ? "0" : database;
        return Optional.of("redis://" + auth + host + ":" + port + "/" + db);
    }

    private Map<String, Object> decrypt(UUID bindingId) {
        String encrypted = controlPlane.encryptedBindingConfiguration(bindingId);
        if (encrypted == null || encrypted.isBlank() || !cipher.configured()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = mapper.readValue(cipher.decrypt(encrypted), new TypeReference<>() {
            });
            return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    static String safeRedisTarget(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        int scheme = uri.indexOf("://");
        int at = uri.indexOf('@');
        String rest = scheme >= 0 && at > scheme ? uri.substring(at + 1) : uri.replaceFirst("(?i)^rediss?://", "");
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }

    private static String encodeUserinfo(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Environment parseEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Environment.from(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
