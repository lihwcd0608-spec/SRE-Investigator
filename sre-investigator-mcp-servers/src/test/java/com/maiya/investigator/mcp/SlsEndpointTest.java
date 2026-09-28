package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.mcp.config.SlsEndpoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlsEndpointTest {
    @Test
    void parsesCommaSeparatedLogstoresAndUsesFirstAsDefaultEntry() {
        SlsEndpoint endpoint = new SlsEndpoint(
                "cn-hangzhou",
                "demo",
                SlsEndpoint.parseLogstores("app-log, gateway-log"),
                "id",
                "secret",
                ""
        );
        assertEquals(List.of("app-log", "gateway-log"), endpoint.logstores());
        assertEquals("demo.cn-hangzhou.log.aliyuncs.com", endpoint.host());
        assertEquals("app-log", endpoint.resolveLogstore(""));
        assertEquals("gateway-log", endpoint.resolveLogstore("gateway-log"));
    }

    @Test
    void allowsAnyLogstoreBeyondBindingEntry() {
        SlsEndpoint endpoint = new SlsEndpoint("cn-hangzhou", "demo", List.of("app-log"), "id", "secret", null);
        assertEquals("other-log", endpoint.resolveLogstore("other-log"));
    }

    @Test
    void requiresLogstoreWhenBindingHasNoEntry() {
        SlsEndpoint endpoint = new SlsEndpoint("cn-hangzhou", "demo", List.of(), "id", "secret", null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> endpoint.resolveLogstore(""));
        assertTrue(exception.getMessage().contains("默认入口"));
    }
}
