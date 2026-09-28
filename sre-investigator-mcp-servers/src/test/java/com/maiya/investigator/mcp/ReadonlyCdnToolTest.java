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

class ReadonlyCdnToolTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void domainDetailRequiresAllowedDomain() {
        UUID projectId = UUID.randomUUID();
        AtomicReference<String> url = new AtomicReference<>();
        ReadonlyCdnTool tool = tool(projectId, """
                {"kind":"cdn","domains":["cdn.example.com"]}
                """, (method, calledUrl, headers, body) -> {
            url.set(calledUrl);
            return new SignedHttpTransport.Response(200, "{\"RequestId\":\"r-1\"}", Map.of());
        });
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> tool.domainDetail(projectId, "TEST", "cdn-test", "")
        );
        assertTrue(missing.getMessage().contains("域名"));
        IllegalArgumentException blocked = assertThrows(
                IllegalArgumentException.class,
                () -> tool.domainDetail(projectId, "TEST", "cdn-test", "other.example.com")
        );
        assertTrue(blocked.getMessage().contains("未开放"));
        Map<String, Object> result = tool.domainDetail(projectId, "TEST", "cdn-test", "cdn.example.com");
        assertEquals(true, result.get("ok"));
        assertTrue(url.get().contains("Action=DescribeCdnDomainDetail"));
        assertTrue(url.get().contains("DomainName=cdn.example.com"));
    }

    @Test
    void usageRejectsWindowLongerThanSevenDays() {
        UUID projectId = UUID.randomUUID();
        ReadonlyCdnTool tool = tool(projectId, """
                {"kind":"cdn","domains":["cdn.example.com"]}
                """, (method, url, headers, body) -> {
            throw new AssertionError("超窗查询不应发请求");
        });
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.usage(
                        projectId,
                        "TEST",
                        "cdn-test",
                        "cdn.example.com",
                        "2026-01-01T00:00:00Z",
                        "2026-01-10T00:00:00Z",
                        "bps"
                )
        );
        assertTrue(exception.getMessage().contains("7 天"));
    }

    private static ReadonlyCdnTool tool(UUID projectId, String json, SignedHttpTransport transport) {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.CDN, "cdn-test", Instant.now()),
                cipher.encrypt(json)
        );
        AliyunCloudProperties properties = new AliyunCloudProperties();
        properties.setAccessKeyId("LTAI-test");
        properties.setAccessKeySecret("secret");
        return new ReadonlyCdnTool(
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
