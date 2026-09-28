package com.maiya.investigator.mcp.aliyun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.mcp.config.AliyunCloudProperties;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AliyunCloudClientTest {
    @Test
    void rejectsUnknownAction() {
        AliyunCloudClient client = AliyunCloudClient.testing(configured(), (method, url, headers, body) -> {
            throw new AssertionError("不应发起请求");
        });
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.arms("cn-hangzhou", "DeleteTrace", Map.of())
        );
        assertTrue(exception.getMessage().contains("白名单"));
    }

    @Test
    void successWithoutCodeField() {
        AtomicReference<String> url = new AtomicReference<>();
        AliyunCloudClient client = AliyunCloudClient.testing(configured(), (method, calledUrl, headers, body) -> {
            url.set(calledUrl);
            return new SignedHttpTransport.Response(200, "{\"RequestId\":\"r-1\",\"TraceApps\":[]}", Map.of());
        });
        Map<String, Object> result = client.arms("cn-hangzhou", "ListTraceApps", Map.of("AppType", "TRACE"));
        assertEquals(true, result.get("ok"));
        assertEquals("ARMS", result.get("service"));
        assertTrue(url.get().contains("Action=ListTraceApps"));
        assertTrue(url.get().contains("Signature="));
    }

    @Test
    void treatsAliyunErrorCodeAsFailure() {
        AliyunCloudClient client = AliyunCloudClient.testing(configured(), (method, url, headers, body) ->
                new SignedHttpTransport.Response(200, "{\"Code\":\"InvalidAccessKeyId.NotFound\",\"Message\":\"key missing\"}", Map.of()));
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.cdn("DescribeUserDomains", Map.of("PageNumber", "1"))
        );
        assertTrue(exception.getMessage().contains("key missing"));
    }

    private static AliyunCloudProperties configured() {
        AliyunCloudProperties properties = new AliyunCloudProperties();
        properties.setAccessKeyId("LTAI-test");
        properties.setAccessKeySecret("secret");
        return properties;
    }
}
