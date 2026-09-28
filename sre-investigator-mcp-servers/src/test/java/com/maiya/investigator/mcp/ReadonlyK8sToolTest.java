package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.infra.persistence.AuditEventJpaRepository;
import com.maiya.investigator.infra.security.SecretCipher;
import com.maiya.investigator.mcp.k8s.KuboardClient;
import com.maiya.investigator.mcp.k8s.SessionHttpTransport;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlyK8sToolTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void listsPodsFromBindingWithMockTransport() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.K8S, "k8s-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"kuboard","baseUrl":"https://kuboard.example.com","username":"ro","password":"secret","cluster":"prod","namespace":"demo"}
                        """)
        );
        ReadonlyK8sTool tool = new ReadonlyK8sTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                KuboardClient.testing(loginThenPods())
        );

        Map<String, Object> result = tool.listPods(projectId, "TEST", "k8s-test", null, null, "Running");
        assertEquals(true, result.get("ok"));
        assertEquals("prod", result.get("cluster"));
        assertEquals(1, result.get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pods = (List<Map<String, Object>>) result.get("pods");
        assertEquals("app-0", pods.get(0).get("name"));
        assertEquals(2, pods.get(0).get("restarts"));
    }

    @Test
    void requiresNamespaceWhenBindingHasNone() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.K8S, "k8s-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"kuboard","baseUrl":"https://kuboard.example.com","username":"ro","password":"secret","cluster":"prod"}
                        """)
        );
        ReadonlyK8sTool tool = new ReadonlyK8sTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                KuboardClient.testing(loginThenPods())
        );
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.listPods(projectId, "TEST", "k8s-test", null, null, null)
        );
        assertTrue(exception.getMessage().contains("namespace"));
    }

    @Test
    void requiresProjectBinding() {
        ReadonlyK8sTool tool = new ReadonlyK8sTool(
                new BindingConnectionLookup(new InMemoryControlPlaneStore(), new SecretCipher(MASTER_KEY), new ObjectMapper()),
                audit(),
                new KuboardClient()
        );
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> tool.listNamespaces(UUID.randomUUID(), "TEST", "k8s-test")
        );
        assertTrue(exception.getMessage().contains("没有已启用"));
    }

    @Test
    void listsAllowedClustersAndRejectsUnknown() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.K8S, "k8s-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"kuboard","baseUrl":"https://kuboard.example.com","username":"ro","password":"secret","cluster":"demo-app","clusters":["demo-app","demo-admin"],"namespace":"demo"}
                        """)
        );
        ReadonlyK8sTool tool = new ReadonlyK8sTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                KuboardClient.testing(loginThenPods())
        );

        Map<String, Object> clusters = tool.listClusters(projectId, "TEST", "k8s-test");
        assertEquals("demo-app", clusters.get("defaultCluster"));
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) clusters.get("clusters");
        assertEquals("demo-app", names.get(0));
        assertTrue(names.contains("demo-admin"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.listPods(projectId, "TEST", "k8s-test", null, null, null, "unknown")
        );
        assertTrue(exception.getMessage().contains("未开放集群"));
    }

    @Test
    void summarizesWarningEventsFirst() {
        List<Map<String, Object>> events = ReadonlyK8sTool.summarizeEvents(Map.of("items", List.of(
                Map.of("type", "Normal", "reason", "Pulled", "message", "ok", "involvedObject", Map.of("kind", "Pod", "name", "a")),
                Map.of("type", "Warning", "reason", "BackOff", "message", "crash", "involvedObject", Map.of("kind", "Pod", "name", "a"))
        )), 10);
        assertEquals("Warning", events.get(0).get("type"));
        assertEquals("Normal", events.get(1).get("type"));
    }

    private static SessionHttpTransport loginThenPods() {
        return (method, url, headers, body) -> {
            if (!"GET".equalsIgnoreCase(method) && !url.contains("/sso/auth/default")) {
                throw new IllegalStateException("拒绝非 GET：" + method);
            }
            if (url.contains("/sso/auth?") && !url.contains("/default")) {
                return new SessionHttpTransport.Response(302, "", Map.of("Location", List.of("/sso/auth/default?req=abc")));
            }
            if (url.contains("/sso/auth/default") && "GET".equalsIgnoreCase(method)) {
                return new SessionHttpTransport.Response(200, "<input name=\"password\">", Map.of());
            }
            if (url.contains("/sso/auth/default") && "POST".equalsIgnoreCase(method)) {
                return new SessionHttpTransport.Response(302, "", Map.of(
                        "Set-Cookie", List.of("KuboardToken=tok; Path=/"),
                        "Location", List.of("/")
                ));
            }
            if (url.contains("/api/v1/namespaces/demo/pods")) {
                return new SessionHttpTransport.Response(200, """
                        {"items":[{"metadata":{"name":"app-0","namespace":"demo"},"spec":{"nodeName":"n1"},"status":{"phase":"Running","containerStatuses":[{"name":"app","restartCount":2,"state":{"waiting":{"reason":"CrashLoopBackOff"}}}]}}]}
                        """, Map.of());
            }
            throw new IllegalStateException("unexpected " + method + " " + url);
        };
    }

    private static AuditTrail audit() {
        return new AuditTrail(new ObjectProvider<>() {
            @Override
            public AuditEventJpaRepository getObject() {
                return null;
            }

            @Override
            public AuditEventJpaRepository getObject(Object... args) {
                return null;
            }

            @Override
            public AuditEventJpaRepository getIfAvailable() {
                return null;
            }

            @Override
            public AuditEventJpaRepository getIfUnique() {
                return null;
            }

            @Override
            public Iterator<AuditEventJpaRepository> iterator() {
                return List.<AuditEventJpaRepository>of().iterator();
            }
        });
    }
}
