package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import com.maiya.investigator.infra.security.SecretCipher;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties.MysqlProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BindingConnectionLookupTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void resolvesEnabledMysqlBindingByAliasAndIgnoresDisabled() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MYSQL, "test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"mysql","host":"127.0.0.1","port":3306,"database":"sre_investigator","username":"root","password":"local"}
                        """)
        );
        store.setBindingEnabled(projectId, store.bindings(projectId).get(0).id(), false);
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MYSQL, "my_test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"mysql","host":"pc-example.rds.aliyuncs.com","port":3306,"database":"demo_video","username":"ns_user_test","password":"secret"}
                        """)
        );

        BindingConnectionLookup lookup = new BindingConnectionLookup(store, cipher, new ObjectMapper());
        MysqlProfile profile = lookup.mysql(projectId, "TEST", "my-test").orElseThrow();

        assertEquals("pc-example.rds.aliyuncs.com", profile.getHost());
        assertEquals("demo_video", profile.getDatabase());
        assertEquals("ns_user_test", profile.getUsername());
        assertEquals("secret", profile.getPassword());
        assertEquals("pc-example.rds.aliyuncs.com/demo_video", lookup.mysqlSummary(projectId, "TEST", "my_test").orElseThrow());
    }

    @Test
    void throwsWhenEnabledMysqlBindingCannotBeDecrypted() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MYSQL, "my_test", Instant.now()),
                "not-a-cipher-payload"
        );

        BindingConnectionLookup lookup = new BindingConnectionLookup(store, cipher, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> lookup.mysql(projectId, "TEST", "my_test"));
    }

    @Test
    void buildsRedisUriFromBindingHost() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.REDIS, "test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"redis","host":"r-example.redis.rds.aliyuncs.com","port":6379,"database":"2","password":"p@ss"}
                        """)
        );

        BindingConnectionLookup lookup = new BindingConnectionLookup(store, cipher, new ObjectMapper());
        String uri = lookup.redisUri(projectId, "TEST", "test").orElseThrow();
        assertTrue(uri.startsWith("redis://:"));
        assertTrue(uri.contains("r-example.redis.rds.aliyuncs.com:6379/2"));
        assertEquals("r-example.redis.rds.aliyuncs.com:6379", lookup.redisSummary(projectId, "TEST", "test").orElseThrow());
    }

    @Test
    void resolvesSlsAndMseBindings() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "sls", Instant.now()),
                cipher.encrypt("""
                        {"kind":"logs","regionId":"cn-hangzhou","project":"demo","logstore":"app-log","accessKeyId":"id","accessKeySecret":"secret"}
                        """)
        );
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MSE, "mse", Instant.now()),
                cipher.encrypt("""
                        {"kind":"mse","regionId":"cn-hangzhou","accessKeyId":"id","accessKeySecret":"secret","gatewayUniqueId":"gw-1","nacosInstanceId":"mse-1"}
                        """)
        );

        BindingConnectionLookup lookup = new BindingConnectionLookup(store, cipher, new ObjectMapper());
        assertEquals("cn-hangzhou/demo/app-log", lookup.slsSummary(projectId, "TEST", "sls").orElseThrow());
        assertTrue(lookup.mseSummary(projectId, "TEST", "mse").orElseThrow().contains("gw=gw-1"));
        assertEquals("demo", lookup.sls(projectId, "TEST", "sls").orElseThrow().project());
    }

    @Test
    void resolvesStarrocksKafkaAndRocketmq() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.STARROCKS, "sr", Instant.now()),
                cipher.encrypt("""
                        {"kind":"starrocks","host":"fe.example.com","port":9030,"database":"ads","username":"ro","password":"p"}
                        """)
        );
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.KAFKA, "kf", Instant.now()),
                cipher.encrypt("""
                        {"kind":"kafka","host":"broker:9092","parameters":"PLAINTEXT"}
                        """)
        );
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.ROCKETMQ, "rmq", Instant.now()),
                cipher.encrypt("""
                        {"kind":"rocketmq","host":"namesrv:9876"}
                        """)
        );
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MONGODB, "mongo-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"mongodb","host":"127.0.0.1","port":27017,"uri":"mongodb://test:secret@mongo.example.com:3717/demo_test"}
                        """)
        );
        BindingConnectionLookup lookup = new BindingConnectionLookup(store, cipher, new ObjectMapper());
        assertEquals("fe.example.com/ads", lookup.starrocksSummary(projectId, "TEST", "sr").orElseThrow());
        assertEquals("broker:9092 PLAINTEXT", lookup.kafkaSummary(projectId, "TEST", "kf").orElseThrow());
        assertEquals("namesrv:9876", lookup.rocketmqSummary(projectId, "TEST", "rmq").orElseThrow());
        assertEquals("mongodb://test:***@mongo.example.com:3717/demo_test", lookup.mongodbSummary(projectId, "TEST", "mongo-test").orElseThrow());
        assertEquals("demo_test", lookup.mongodb(projectId, "TEST", "mongo-test").orElseThrow().defaultDatabase());
    }

    @Test
    void resolvesKuboardBinding() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.K8S, "k8s", Instant.now()),
                cipher.encrypt("""
                        {"kind":"kuboard","baseUrl":"https://kuboard.example.com","username":"ro","password":"secret","cluster":"prod","namespace":"demo"}
                        """)
        );

        BindingConnectionLookup lookup = new BindingConnectionLookup(store, cipher, new ObjectMapper());
        assertTrue(lookup.k8sSummary(projectId, "TEST", "k8s").orElseThrow().contains("cluster=prod"));
        assertEquals("demo", lookup.k8s(projectId, "TEST", "k8s").orElseThrow().namespace());
        assertEquals("prod", lookup.k8s(projectId, "TEST", "k8s").orElseThrow().cluster());
        assertTrue(BindingConnectionLookup.k8sEndpoint(Map.of(
                "baseUrl", "https://kuboard.example.com",
                "username", "ro"
        )).isEmpty());
        assertTrue(BindingConnectionLookup.k8sCredentials(Map.of(
                "baseUrl", "https://kuboard.example.com",
                "username", "ro",
                "password", "secret"
        )).isPresent());
    }

    @Test
    void parsesMultipleK8sClustersAndRejectsUnlisted() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.K8S, "k8s", Instant.now()),
                cipher.encrypt("""
                        {"kind":"kuboard","baseUrl":"https://kuboard.example.com","username":"ro","password":"secret","cluster":"demo-app","clusters":["demo-app","demo-admin"]}
                        """)
        );

        BindingConnectionLookup lookup = new BindingConnectionLookup(store, cipher, new ObjectMapper());
        assertEquals("demo-app", lookup.k8s(projectId, "TEST", "k8s").orElseThrow().cluster());
        assertEquals("demo-admin", lookup.k8s(projectId, "TEST", "k8s", "demo-admin").orElseThrow().cluster());
        List<String> allowed = lookup.k8sAllowedClusters(projectId, "TEST", "k8s");
        assertEquals("demo-app", allowed.get(0));
        assertTrue(allowed.contains("demo-admin"));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> lookup.k8s(projectId, "TEST", "k8s", "unknown")
        );
        assertTrue(exception.getMessage().contains("未开放集群"));
    }

    @Test
    void k8sClustersJsonFallsBackToSingleCluster() {
        Map<String, Object> config = Map.of(
                "baseUrl", "https://kuboard.example.com",
                "username", "ro",
                "password", "secret",
                "cluster", "prod"
        );
        assertEquals("prod", BindingConnectionLookup.parseClusterNames(config).get(0));
        assertEquals("prod", BindingConnectionLookup.k8sEndpoint(config).orElseThrow().cluster());
        Map<String, Object> multi = Map.of(
                "baseUrl", "https://kuboard.example.com",
                "username", "ro",
                "password", "secret",
                "clusters", java.util.List.of("b", "a"),
                "cluster", "a"
        );
        List<String> names = BindingConnectionLookup.parseClusterNames(multi);
        assertEquals("a", names.get(0));
        assertEquals("b", names.get(1));
    }
}
