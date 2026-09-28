package com.maiya.investigator.boot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class FeishuRedirectsTest {
    @Test
    void usesPublicHostWhenOpeningTunnel() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.addHeader("Host", "gf3032rm5129.vicp.fun");
        assertEquals(
                "http://gf3032rm5129.vicp.fun/api/auth/feishu/callback",
                FeishuRedirects.callbackUri(request, "http://localhost:8790/api/auth/feishu/callback")
        );
    }

    @Test
    void keepsLocalhostPortWhenOpeningLocalConsole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.addHeader("Host", "localhost:8790");
        assertEquals(
                "http://localhost:8790/api/auth/feishu/callback",
                FeishuRedirects.callbackUri(request, "http://example/unused")
        );
    }

    @Test
    void prefersForwardedHostAndProto() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.addHeader("Host", "127.0.0.1:8790");
        request.addHeader("X-Forwarded-Host", "gf3032rm5129.vicp.fun");
        request.addHeader("X-Forwarded-Proto", "http");
        assertEquals(
                "http://gf3032rm5129.vicp.fun/api/auth/feishu/callback",
                FeishuRedirects.callbackUri(request, "http://localhost:8790/api/auth/feishu/callback")
        );
    }

    @Test
    void fallsBackWhenHostMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(
                "http://localhost:8790/api/auth/feishu/callback",
                FeishuRedirects.callbackUri(request, "http://localhost:8790/api/auth/feishu/callback")
        );
    }
}
