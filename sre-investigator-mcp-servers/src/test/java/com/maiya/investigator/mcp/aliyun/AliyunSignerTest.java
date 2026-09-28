package com.maiya.investigator.mcp.aliyun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliyunSignerTest {
    @Test
    void slsAuthorizationIsDeterministic() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-log-apiversion", "0.6.0");
        headers.put("x-log-signaturemethod", "hmac-sha1");
        headers.put("x-log-bodyrawsize", "0");
        String auth = AliyunSigner.slsAuthorization(
                "GET",
                "/logstores?offset=0&size=500",
                "Sun, 30 Aug 2026 12:00:00 GMT",
                headers,
                "LTAI-test",
                "secret"
        );
        assertTrue(auth.startsWith("LOG LTAI-test:"));
        assertEquals(auth, AliyunSigner.slsAuthorization(
                "GET",
                "/logstores?offset=0&size=500",
                "Sun, 30 Aug 2026 12:00:00 GMT",
                headers,
                "LTAI-test",
                "secret"
        ));
    }

    @Test
    void slsCanonicalResourceKeepsRawQueryValue() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", "traceId: abc");
        params.put("type", "log");
        params.put("from", "1");
        assertEquals(
                "/logstores/app-log?from=1&query=traceId: abc&type=log",
                AliyunSigner.slsCanonicalResource("/logstores/app-log", params)
        );
        assertEquals("from=1&query=traceId%3A%20abc&type=log", AliyunSigner.canonicalQuery(params));
    }

    @Test
    void rpcSignaturePercentEncodes() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action", "ListGateway");
        params.put("Format", "JSON");
        params.put("Version", "2019-05-31");
        params.put("AccessKeyId", "LTAI-test");
        String signature = AliyunSigner.rpcSignature("GET", params, "secret");
        assertEquals(signature, AliyunSigner.rpcSignature("GET", params, "secret"));
        assertTrue(AliyunSigner.percentEncode("a b").contains("%20"));
    }

    @Test
    void formatsDates() {
        Instant instant = Instant.parse("2026-08-30T12:00:00Z");
        assertEquals("2026-08-30T12:00:00Z", AliyunSigner.iso8601(instant));
        assertTrue(AliyunSigner.rfc822Date(instant).contains("2026"));
    }
}
