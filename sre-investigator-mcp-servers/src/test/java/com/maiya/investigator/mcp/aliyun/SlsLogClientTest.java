package com.maiya.investigator.mcp.aliyun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.mcp.config.SlsEndpoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SlsLogClientTest {
    @Test
    void querySignsUnencodedResourceAndSendsEncodedUrl() {
        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<Map<String, String>> sent = new AtomicReference<>();
        SlsLogClient client = SlsLogClient.testing((method, requestUrl, headers, body) -> {
            url.set(requestUrl);
            sent.set(headers);
            return new SignedHttpTransport.Response(200, "[]", Map.of());
        });
        SlsEndpoint endpoint = new SlsEndpoint(
                "us-west-1",
                "k8s-log-demo",
                new ArrayList<>(),
                "LTAI-test",
                "secret",
                "us-west-1.log.aliyuncs.com"
        );
        client.getLogs(endpoint, "demo-app-video-query", "traceId: abc", 1, 2, 50);

        assertTrue(url.get().contains("query=traceId%3A%20abc"));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-log-apiversion", "0.6.0");
        headers.put("x-log-signaturemethod", "hmac-sha1");
        headers.put("x-log-bodyrawsize", "0");
        String expected = AliyunSigner.slsAuthorization(
                "GET",
                "/logstores/demo-app-video-query?from=1&line=50&offset=0&query=traceId: abc&reverse=true&to=2&type=log",
                sent.get().get("Date"),
                headers,
                "LTAI-test",
                "secret"
        );
        assertEquals(expected, sent.get().get("Authorization"));
    }

    @Test
    void retriesUntilProgressComplete() {
        AtomicInteger calls = new AtomicInteger();
        SlsLogClient client = SlsLogClient.testing((method, requestUrl, headers, body) -> {
            int n = calls.incrementAndGet();
            if (n < 3) {
                return new SignedHttpTransport.Response(
                        200,
                        "[]",
                        Map.of("X-Log-Progress", "Incomplete")
                );
            }
            return new SignedHttpTransport.Response(
                    200,
                    "[{\"traceId\":\"72fb5cdae565fd9a6742652cdf59e44f\"}]",
                    Map.of("x-log-progress", "Complete")
            );
        });
        SlsEndpoint endpoint = new SlsEndpoint(
                "us-west-1",
                "k8s-log-demo",
                new ArrayList<>(),
                "LTAI-test",
                "secret",
                "us-west-1.log.aliyuncs.com"
        );
        Map<String, Object> result = client.getLogs(
                endpoint,
                "demo-app-video-query",
                "72fb5cdae565fd9a6742652cdf59e44f",
                1,
                2,
                50
        );
        assertEquals(3, calls.get());
        assertEquals(1, result.get("count"));
        assertEquals("Complete", result.get("progress"));
        assertTrue(!Boolean.TRUE.equals(result.get("incomplete")));
    }

    @Test
    void readsLogsFromObjectBody() {
        SlsLogClient client = SlsLogClient.testing((method, requestUrl, headers, body) ->
                new SignedHttpTransport.Response(
                        200,
                        "{\"count\":1,\"logs\":[{\"traceId\":\"abc\"}]}",
                        Map.of("x-log-progress", "Complete")
                ));
        SlsEndpoint endpoint = new SlsEndpoint(
                "us-west-1",
                "k8s-log-demo",
                new ArrayList<>(),
                "LTAI-test",
                "secret",
                "us-west-1.log.aliyuncs.com"
        );
        Map<String, Object> result = client.getLogs(endpoint, "app-log", "abc", 1, 2, 50);
        assertEquals(1, result.get("count"));
    }
}
