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
import com.maiya.investigator.mcp.aliyun.AliyunCloudClient;
import com.maiya.investigator.mcp.aliyun.SignedHttpTransport;
import com.maiya.investigator.mcp.config.AliyunCloudProperties;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlyArmsToolTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void listAppsReturnsBindingWhitelistWithoutCallingOpenApi() {
        UUID projectId = UUID.randomUUID();
        ReadonlyArmsTool tool = tool(projectId, """
                {"kind":"arms","regionId":"cn-hangzhou","applications":["demo-app-order"]}
                """, (method, url, headers, body) -> {
            throw new AssertionError("白名单应用不应调用 ListTraceApps");
        });
        Map<String, Object> result = tool.listApps(projectId, "TEST", "arms-test");
        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("count"));
        assertTrue(String.valueOf(result.get("applications")).contains("demo-app-order"));
    }

    @Test
    void searchTracesRequiresApplicationAndSignsRequest() {
        UUID projectId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        ReadonlyArmsTool tool = tool(projectId, """
                {"kind":"arms","regionId":"cn-hangzhou","applications":["demo-app-order"]}
                """, (method, url, headers, body) -> {
            calls.incrementAndGet();
            assertTrue(url.contains("Action=SearchTraces"));
            assertTrue(url.contains("ServiceName=demo-app-order"));
            return new SignedHttpTransport.Response(200, "{\"RequestId\":\"r-1\",\"TraceInfos\":[]}", Map.of());
        });
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> tool.searchTraces(projectId, "TEST", "arms-test", "", null, null, null, null, 20)
        );
        assertTrue(missing.getMessage().contains("应用"));
        Map<String, Object> result = tool.searchTraces(
                projectId, "TEST", "arms-test", "demo-app-order", null, "-1h", "now", null, 80
        );
        assertEquals(true, result.get("ok"));
        assertEquals(1, calls.get());
    }

    private static ReadonlyArmsTool tool(UUID projectId, String json, SignedHttpTransport transport) {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.ARMS, "arms-test", Instant.now()),
                cipher.encrypt(json)
        );
        AliyunCloudProperties properties = new AliyunCloudProperties();
        properties.setAccessKeyId("LTAI-test");
        properties.setAccessKeySecret("secret");
        return new ReadonlyArmsTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                AliyunCloudClient.testing(properties, transport)
        );
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
