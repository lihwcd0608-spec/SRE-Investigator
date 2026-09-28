package com.maiya.investigator.mcp.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.mcp.config.KuboardEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KuboardClientTest {
    private static final KuboardEndpoint ENDPOINT = new KuboardEndpoint(
            "https://kuboard.example.com",
            "ro",
            "secret",
            "prod",
            "default",
            false
    );

    @Test
    void logsInThenListsPodsWithGetOnly() {
        RecordingTransport transport = new RecordingTransport();
        KuboardClient client = KuboardClient.testing(transport);

        Map<String, Object> pods = client.getJson(ENDPOINT, "/api/v1/namespaces/default/pods");

        assertEquals(1, ((List<?>) pods.get("items")).size());
        assertTrue(transport.methods.contains("POST"));
        assertTrue(transport.urls.stream().anyMatch(url -> url.contains("/sso/auth/default")));
        assertTrue(transport.urls.stream().anyMatch(url -> url.contains("/k8s-api/prod/api/v1/namespaces/default/pods")));
        assertTrue(transport.postedBodies.stream().anyMatch(body -> body.contains("%7B%22password%22") || body.contains("passcode")));
        for (int i = 0; i < transport.methods.size(); i++) {
            if (!"GET".equals(transport.methods.get(i))) {
                assertEquals("POST", transport.methods.get(i));
                assertTrue(transport.urls.get(i).contains("/sso/auth/default"));
            }
        }
    }

    @Test
    void stripsKubernetesConsolePathAndLogsInAtOrigin() {
        KuboardEndpoint pasted = new KuboardEndpoint(
                "http://kuboard-test.netshort.net/kubernetes/netshort-test/",
                "ro",
                "secret",
                "netshort-test",
                "demo-app",
                false
        );
        assertEquals("http://kuboard-test.netshort.net", pasted.baseUrl());
        assertEquals(
                "http://kuboard-test.netshort.net",
                new KuboardEndpoint("http://kuboard-test.netshort.net/kubernetes", "ro", "x", "c", "", false).baseUrl()
        );
        RecordingTransport transport = new RecordingTransport();
        KuboardClient.testing(transport).login(pasted);
        assertTrue(transport.urls.stream().allMatch(url -> url.startsWith("http://kuboard-test.netshort.net/")));
        assertTrue(transport.urls.stream().noneMatch(url -> url.contains("/kubernetes/")));
    }

    @Test
    void rejectsUnsafePaths() {
        assertThrows(IllegalArgumentException.class, () -> KuboardClient.requireSafeGetPath("/exec"));
        assertThrows(IllegalArgumentException.class, () -> KuboardClient.requireSafeGetPath("../api/v1/pods"));
        assertThrows(IllegalArgumentException.class, () -> KuboardClient.requireSafeGetPath("/api/v1/pods/../secrets"));
    }

    @Test
    void loginFailsWhenPasswordRejected() {
        SessionHttpTransport transport = (method, url, headers, body) -> {
            if (url.contains("/sso/auth?") && !url.contains("/default")) {
                return new SessionHttpTransport.Response(302, "", Map.of("Location", List.of("/sso/auth/default?req=abc")));
            }
            if ("GET".equals(method) && url.contains("/sso/auth/default")) {
                return new SessionHttpTransport.Response(200, "<input name=\"password\">", Map.of());
            }
            if ("POST".equals(method)) {
                return new SessionHttpTransport.Response(200, "invalid login", Map.of());
            }
            throw new IllegalStateException("unexpected " + method + " " + url);
        };
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> KuboardClient.testing(transport).login(ENDPOINT)
        );
        assertTrue(exception.getMessage().contains("用户名和密码"));
    }

    @Test
    void listsClusterNamesFromKuboardApi() {
        SessionHttpTransport transport = (method, url, headers, body) -> {
            if (url.contains("/sso/auth?") && !url.contains("/default")) {
                return new SessionHttpTransport.Response(302, "", Map.of("Location", List.of("/sso/auth/default?req=abc")));
            }
            if ("GET".equals(method) && url.contains("/sso/auth/default")) {
                return new SessionHttpTransport.Response(200, "<input name=\"password\">", Map.of());
            }
            if ("POST".equals(method)) {
                return new SessionHttpTransport.Response(302, "", Map.of(
                        "Location", List.of("/"),
                        "Set-Cookie", List.of("KuboardToken=tok; Path=/")
                ));
            }
            if (url.contains("/kuboard-api/kind/Cluster")) {
                return new SessionHttpTransport.Response(200, """
                        {"items":[{"metadata":{"name":"demo-app"}},{"metadata":{"name":"demo-admin"}}]}
                        """, Map.of());
            }
            throw new IllegalStateException("unexpected " + method + " " + url);
        };
        List<String> names = KuboardClient.testing(transport).listClusterNames(ENDPOINT);
        assertEquals("demo-app", names.get(0));
        assertEquals("demo-admin", names.get(1));
    }

    @Test
    void mapsKubernetesRbac403ToActionableMessageWithoutRelogin() {
        String forbiddenBody = "{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\","
                + "\"message\":\"pods is forbidden: User \\\"changfeng\\\" cannot list resource \\\"pods\\\" "
                + "in API group \\\"\\\" in the namespace \\\"nacos\\\"\",\"reason\":\"Forbidden\","
                + "\"details\":{\"kind\":\"pods\"},\"code\":403}";

        assertTrue(KuboardClient.isKubernetesForbidden(forbiddenBody));
        String described = KuboardClient.describeForbidden(forbiddenBody);
        assertTrue(described.contains("K8s 无权限"));
        assertTrue(described.contains("changfeng"));
        assertTrue(described.contains("nacos"));
        assertTrue(described.contains("pods"));

        AtomicInteger k8sGets = new AtomicInteger();
        SessionHttpTransport transport = (method, url, headers, body) -> {
            if (url.contains("/sso/auth?") && !url.contains("/default")) {
                return new SessionHttpTransport.Response(302, "", Map.of("Location", List.of("/sso/auth/default?req=abc")));
            }
            if ("GET".equals(method) && url.contains("/sso/auth/default")) {
                return new SessionHttpTransport.Response(200, "<input name=\"password\">", Map.of());
            }
            if ("POST".equals(method)) {
                return new SessionHttpTransport.Response(302, "", Map.of(
                        "Location", List.of("/"),
                        "Set-Cookie", List.of("KuboardToken=tok; Path=/")
                ));
            }
            if (url.contains("/k8s-api/") && url.contains("/pods")) {
                k8sGets.incrementAndGet();
                return new SessionHttpTransport.Response(403, forbiddenBody, Map.of());
            }
            throw new IllegalStateException("unexpected " + method + " " + url);
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> KuboardClient.testing(transport).getJson(ENDPOINT, "/api/v1/namespaces/nacos/pods")
        );
        assertTrue(error.getMessage().contains("K8s 无权限"));
        assertTrue(error.getMessage().contains("changfeng"));
        assertEquals(1, k8sGets.get(), "RBAC 403 不应触发重登后再打一遍 K8s API");
    }

    private static final class RecordingTransport implements SessionHttpTransport {
        private final List<String> methods = new ArrayList<>();
        private final List<String> urls = new ArrayList<>();
        private final List<String> postedBodies = new ArrayList<>();

        @Override
        public Response exchange(String method, String url, Map<String, String> headers, String body) {
            if (!"GET".equalsIgnoreCase(method) && !url.contains("/sso/auth/default")) {
                throw new IllegalStateException("拒绝非 GET：" + method + " " + url);
            }
            methods.add(method);
            urls.add(url);
            if ("POST".equalsIgnoreCase(method)) {
                postedBodies.add(body == null ? "" : body);
            }
            if (url.contains("/sso/auth?") && !url.contains("/default")) {
                return new Response(302, "", Map.of("Location", List.of("/sso/auth/default?req=abc123")));
            }
            if (url.contains("/sso/auth/default") && "GET".equalsIgnoreCase(method)) {
                return new Response(200, "<form><input name=\"password\"></form>", Map.of());
            }
            if (url.contains("/sso/auth/default") && "POST".equalsIgnoreCase(method)) {
                return new Response(302, "", Map.of(
                        "Location", List.of("/"),
                        "Set-Cookie", List.of("KuboardToken=test-token; Path=/")
                ));
            }
            if (url.contains("/api/v1/namespaces/default/pods")) {
                return new Response(200, """
                        {"items":[{"metadata":{"name":"app-0","namespace":"default"},"spec":{"nodeName":"n1"},"status":{"phase":"Running"}}]}
                        """, Map.of());
            }
            throw new IllegalStateException("unexpected " + method + " " + url);
        }
    }
}
