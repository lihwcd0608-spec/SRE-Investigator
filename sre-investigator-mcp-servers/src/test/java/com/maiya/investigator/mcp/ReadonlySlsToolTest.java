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
import com.maiya.investigator.mcp.aliyun.SignedHttpTransport;
import com.maiya.investigator.mcp.aliyun.SlsLogClient;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReadonlySlsToolTest {
    private static final String MASTER_KEY = "NQPF9Blwik4godZmbz3FXo++wXei9Y4MD6cpSBTCPFQ=";

    @Test
    void prefersStandardKeyValueWhenIndexed() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "sls-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"logs","regionId":"cn-hangzhou","project":"demo","logstore":"app-log","accessKeyId":"id","accessKeySecret":"secret"}
                        """)
        );
        SignedHttpTransport transport = (method, url, headers, body) -> {
            assertTrue(url.contains("/logstores/app-log"));
            assertTrue(url.contains("type=log"));
            assertTrue(url.contains("query=traceId%3A%20abc") || url.contains("query=traceId%3A+abc"));
            return new SignedHttpTransport.Response(200, "[{\"traceId\":\"abc\",\"__time__\":1}]", Map.of());
        };
        ReadonlySlsTool tool = new ReadonlySlsTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                SlsLogClient.testing(transport)
        );

        Map<String, Object> result = tool.query(projectId, "TEST", "sls-test", "traceId: abc", null, null, 20, null);
        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("count"));
        assertEquals("app-log", result.get("logstore"));
        assertEquals("traceId: abc", result.get("query"));
    }

    @Test
    void retriesBareIdWhenOldJsonFragmentReturnsEmpty() {
        // 复现：模型按旧提示写 traceId\":\"id，已建键值索引的 logstore 全文零命中
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "sls-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"logs","regionId":"cn-hangzhou","project":"demo","logstore":"demo-app-video-query","accessKeyId":"id","accessKeySecret":"secret"}
                        """)
        );
        AtomicInteger calls = new AtomicInteger();
        SignedHttpTransport transport = (method, url, headers, body) -> {
            calls.incrementAndGet();
            if (url.contains("query=72fb5cdae565fd9a6742652cdf59e44f")) {
                return new SignedHttpTransport.Response(
                        200,
                        "[{\"traceId\":\"72fb5cdae565fd9a6742652cdf59e44f\",\"text\":\"高光图片失败\"}]",
                        Map.of("x-log-progress", "Complete")
                );
            }
            if (url.contains("query=traceId%3A%2072fb5cdae565fd9a6742652cdf59e44f")
                    || url.contains("query=traceId%3A+72fb5cdae565fd9a6742652cdf59e44f")) {
                return new SignedHttpTransport.Response(
                        200,
                        "[{\"traceId\":\"72fb5cdae565fd9a6742652cdf59e44f\",\"text\":\"高光图片失败\"}]",
                        Map.of("x-log-progress", "Complete")
                );
            }
            // 旧碎片写法：成功但 count=0
            return new SignedHttpTransport.Response(200, "[]", Map.of("x-log-progress", "Complete"));
        };
        ReadonlySlsTool tool = new ReadonlySlsTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                SlsLogClient.testing(transport)
        );

        Map<String, Object> result = tool.query(
                projectId,
                "TEST",
                "sls-test",
                "traceId\":\"72fb5cdae565fd9a6742652cdf59e44f",
                null,
                null,
                20,
                "demo-app-video-query"
        );
        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("count"));
        assertTrue(calls.get() >= 2, "应至少尝试旧写法后再换候选");
        assertTrue(String.valueOf(result.get("query")).contains("72fb5cdae565fd9a6742652cdf59e44f"));
        assertTrue(String.valueOf(result.get("note")).contains("自动改用"));
    }

    @Test
    void fallsBackWhenKeyValueIndexMissing() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "sls-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"logs","regionId":"cn-hangzhou","project":"demo","logstore":"app-log","accessKeyId":"id","accessKeySecret":"secret"}
                        """)
        );
        SignedHttpTransport transport = (method, url, headers, body) -> {
            if (url.contains("query=traceId%3A%20abc") || url.contains("query=traceId%3A+abc")) {
                return new SignedHttpTransport.Response(
                        400,
                        "{\"errorCode\":\"ParameterInvalid\",\"errorMessage\":\"key (traceId) is not config as key value config,if symbol : is  in your log,please wrap : with quotation mark \\\"\"}",
                        Map.of()
                );
            }
            if (url.contains("query=traceId%22%3A%22abc")) {
                return new SignedHttpTransport.Response(200, "[{\"traceId\":\"abc\"}]", Map.of());
            }
            if (url.contains("query=abc") || url.contains("query=%22traceId%3A%20abc%22")) {
                return new SignedHttpTransport.Response(200, "[{\"traceId\":\"abc\"}]", Map.of());
            }
            return new SignedHttpTransport.Response(200, "[]", Map.of());
        };
        ReadonlySlsTool tool = new ReadonlySlsTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                SlsLogClient.testing(transport)
        );

        Map<String, Object> result = tool.query(projectId, "TEST", "sls-test", "traceId: abc", null, null, 20, null);
        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("count"));
        assertTrue(String.valueOf(result.get("note")).contains("自动改用"));
    }

    @Test
    void requiresProjectBinding() {
        ReadonlySlsTool tool = new ReadonlySlsTool(
                new BindingConnectionLookup(new InMemoryControlPlaneStore(), new SecretCipher(MASTER_KEY), new ObjectMapper()),
                audit(),
                new SlsLogClient()
        );
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> tool.listLogstores(UUID.randomUUID(), "TEST", "sls-test")
        );
        assertTrue(exception.getMessage().contains("没有已启用"));
    }

    @Test
    void retriesRecentWindowWhenModelInventedStaleYear() {
        // 复现：模型把 from/to 编成 2024-06-01，真实日志在当前近 7 天
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "sls-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"logs","regionId":"cn-hangzhou","project":"demo","logstore":"demo-app-video-query","accessKeyId":"id","accessKeySecret":"secret"}
                        """)
        );
        AtomicInteger staleCalls = new AtomicInteger();
        AtomicInteger recentCalls = new AtomicInteger();
        SignedHttpTransport transport = (method, url, headers, body) -> {
            if (url.contains("from=1717")) {
                staleCalls.incrementAndGet();
                return new SignedHttpTransport.Response(200, "[]", Map.of("x-log-progress", "Complete"));
            }
            recentCalls.incrementAndGet();
            return new SignedHttpTransport.Response(
                    200,
                    "[{\"traceId\":\"72fb5cdae565fd9a6742652cdf59e44f\",\"text\":\"高光图片失败\"}]",
                    Map.of("x-log-progress", "Complete")
            );
        };
        ReadonlySlsTool tool = new ReadonlySlsTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                SlsLogClient.testing(transport)
        );

        Map<String, Object> result = tool.query(
                projectId,
                "TEST",
                "sls-test",
                "72fb5cdae565fd9a6742652cdf59e44f",
                "2024-06-01 00:00:00",
                "2024-06-08 00:00:00",
                20,
                "demo-app-video-query"
        );
        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("count"));
        assertTrue(staleCalls.get() > 0);
        assertTrue(recentCalls.get() > 0);
        assertTrue(String.valueOf(result.get("note")).contains("近 7 天"));
        assertEquals(1_717_171_200L, ((Number) result.get("originalFrom")).longValue());
    }

    @Test
    void stripsClassNamesFromNoisyTraceQuery() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "sls-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"logs","regionId":"cn-hangzhou","project":"demo","logstore":"demo-app-video-query","accessKeyId":"id","accessKeySecret":"secret"}
                        """)
        );
        AtomicInteger calls = new AtomicInteger();
        SignedHttpTransport transport = (method, url, headers, body) -> {
            calls.incrementAndGet();
            assertTrue(!url.contains("VmsTheaterController"), "噪声 query 不得把类名发给 SLS");
            assertTrue(!url.toLowerCase().contains("and+"), "不得把 AND 条件发给 SLS");
            if (url.contains("query=72fb5cdae565fd9a6742652cdf59e44f")
                    || url.contains("query=traceId%3A%2072fb5cdae565fd9a6742652cdf59e44f")
                    || url.contains("query=traceId%3A+72fb5cdae565fd9a6742652cdf59e44f")) {
                return new SignedHttpTransport.Response(
                        200,
                        "[{\"traceId\":\"72fb5cdae565fd9a6742652cdf59e44f\"}]",
                        Map.of("x-log-progress", "Complete")
                );
            }
            return new SignedHttpTransport.Response(200, "[]", Map.of("x-log-progress", "Complete"));
        };
        ReadonlySlsTool tool = new ReadonlySlsTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                SlsLogClient.testing(transport)
        );

        Map<String, Object> result = tool.query(
                projectId,
                "TEST",
                "sls-test",
                "traceId:72fb5cdae565fd9a6742652cdf59e44f AND VmsTheaterController OR TheaterController",
                null,
                null,
                20,
                "demo-app-video-query"
        );
        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("count"));
        assertEquals("72fb5cdae565fd9a6742652cdf59e44f", result.get("query"));
        assertTrue(calls.get() >= 1);
    }

    @Test
    void scansOlderDayWhenNewestSliceEmpty() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        SecretCipher cipher = new SecretCipher(MASTER_KEY);
        UUID projectId = UUID.randomUUID();
        store.saveBinding(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.LOGS, "sls-test", Instant.now()),
                cipher.encrypt("""
                        {"kind":"logs","regionId":"cn-hangzhou","project":"demo","logstore":"demo-app-video-query","accessKeyId":"id","accessKeySecret":"secret"}
                        """)
        );
        long now = Instant.now().getEpochSecond();
        AtomicInteger emptyNewest = new AtomicInteger();
        AtomicInteger olderHits = new AtomicInteger();
        SignedHttpTransport transport = (method, url, headers, body) -> {
            long from = Long.parseLong(url.replaceAll(".*[?&]from=(\\d+).*", "$1"));
            if (from >= now - 86_400L - 120L) {
                emptyNewest.incrementAndGet();
                return new SignedHttpTransport.Response(200, "[]", Map.of("x-log-progress", "Complete"));
            }
            olderHits.incrementAndGet();
            return new SignedHttpTransport.Response(
                    200,
                    "[{\"traceId\":\"72fb5cdae565fd9a6742652cdf59e44f\",\"text\":\"两天前的日志\"}]",
                    Map.of("x-log-progress", "Complete")
            );
        };
        ReadonlySlsTool tool = new ReadonlySlsTool(
                new BindingConnectionLookup(store, cipher, new ObjectMapper()),
                audit(),
                SlsLogClient.testing(transport)
        );

        Map<String, Object> result = tool.query(
                projectId,
                "TEST",
                "sls-test",
                "72fb5cdae565fd9a6742652cdf59e44f",
                null,
                null,
                20,
                "demo-app-video-query"
        );
        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("count"));
        assertTrue(emptyNewest.get() > 0);
        assertTrue(olderHits.get() > 0);
        assertTrue(String.valueOf(result.get("note")).contains("24 小时"));
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
