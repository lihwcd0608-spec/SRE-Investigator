package com.maiya.investigator.boot.auth;

import jakarta.servlet.http.HttpServletRequest;

final class FeishuRedirects {
    static final String CALLBACK_PATH = "/api/auth/feishu/callback";

    private FeishuRedirects() {
    }

    static String callbackUri(HttpServletRequest request, String fallback) {
        String host = firstCsv(header(request, "X-Forwarded-Host"));
        if (host == null || host.isBlank()) {
            host = header(request, "Host");
        }
        if (host == null || host.isBlank()) {
            return trim(fallback);
        }
        host = firstCsv(host);
        if (host == null || !host.matches("[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]+)?")) {
            return trim(fallback);
        }
        String proto = firstCsv(header(request, "X-Forwarded-Proto"));
        if (proto == null || proto.isBlank()) {
            proto = request.getScheme();
        }
        proto = proto == null ? "http" : proto.toLowerCase();
        if (!"http".equals(proto) && !"https".equals(proto)) {
            proto = "http";
        }
        return proto + "://" + host + CALLBACK_PATH;
    }

    private static String header(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        return request.getHeader(name);
    }

    private static String firstCsv(String value) {
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma < 0 ? value : value.substring(0, comma)).trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
