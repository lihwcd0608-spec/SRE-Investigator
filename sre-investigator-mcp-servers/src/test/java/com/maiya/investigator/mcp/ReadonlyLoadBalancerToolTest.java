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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlyLoadBalancerToolTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void listAlbUsesWhitelistAction() {
        UUID projectId = UUID.randomUUID();
        AtomicReference<String> url = new AtomicReference<>();
        ReadonlyLoadBalancerTool tool = tool(projectId, """
                {"kind":"load_balancer","regionId":"cn-hangzhou","serviceType":"ALB","instanceIds":["alb-1"]}
                """, (method, calledUrl, headers, body) -> {
            url.set(calledUrl);
            return new SignedHttpTransport.Response(200, "{\"RequestId\":\"r-1\",\"LoadBalancers\":[]}", Map.of());
        });
        Map<String, Object> result = tool.list(projectId, "TEST", "lb-test");
        assertEquals(true, result.get("ok"));
        assertTrue(url.get().contains("Action=ListLoadBalancers"));
        assertTrue(url.get().contains("LoadBalancerIds.1=alb-1"));
    }

    @Test
    void healthRejectsInstanceOutsideWhitelist() {
        UUID projectId = UUID.randomUUID();
        ReadonlyLoadBalancerTool tool = tool(projectId, """
                {"kind":"load_balancer","regionId":"cn-hangzhou","serviceType":"CLB","instanceIds":["lb-1"]}
                """, (method, url, headers, body) -> {
            throw new AssertionError("未开放实例不应发请求");
        });
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.health(projectId, "TEST", "lb-test", "lb-other", 80)
        );
        assertTrue(exception.getMessage().contains("未开放"));
    }

    private static ReadonlyLoadBalancerTool tool(UUID projectId, String json, SignedHttpTransport transport) {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOAD_BALANCER, "lb-test", Instant.now()),
                cipher.encrypt(json)
        );
        AliyunCloudProperties properties = new AliyunCloudProperties();
        properties.setAccessKeyId("LTAI-test");
        properties.setAccessKeySecret("secret");
        return new ReadonlyLoadBalancerTool(
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
