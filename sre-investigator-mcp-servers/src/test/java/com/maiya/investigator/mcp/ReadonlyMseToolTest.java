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
import com.maiya.investigator.mcp.aliyun.MseRpcClient;
import com.maiya.investigator.mcp.aliyun.SignedHttpTransport;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlyMseToolTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void listRoutesUsesGatewayIdAndMockTransport() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MSE, "mse-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"mse","regionId":"cn-hangzhou","accessKeyId":"id","accessKeySecret":"secret","gatewayUniqueId":"gw-1","nacosInstanceId":"mse-1"}
                        """)
        );
        AtomicReference<String> url = new AtomicReference<>();
        SignedHttpTransport transport = (method, calledUrl, headers, body) -> {
            url.set(calledUrl);
            return new SignedHttpTransport.Response(200, "{\"Success\":true,\"Data\":{\"Result\":[]}}", Map.of());
        };
        ReadonlyMseTool tool = new ReadonlyMseTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                MseRpcClient.testing(transport)
        );

        Map<String, Object> result = tool.listRoutes(projectId, "TEST", "mse-test", "/api");
        assertEquals(true, result.get("ok"));
        assertEquals("ListGatewayRoute", result.get("action"));
        assertTrue(url.get().contains("Action=ListGatewayRoute"));
        assertTrue(url.get().contains("GatewayUniqueId=gw-1"));
    }

    @Test
    void governanceRejectsUnknownRuleType() {
        ReadonlyMseTool tool = new ReadonlyMseTool(
                new BindingConnectionLookup(new InMemoryControlPlaneStore(), new SecretCipher(MASTER_KEY), new ObjectMapper()),
                audit(),
                new MseRpcClient()
        );
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.listGovernance(UUID.randomUUID(), "TEST", "mse", "app", "delete-all", "default")
        );
        assertTrue(exception.getMessage().contains("不支持"));
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
