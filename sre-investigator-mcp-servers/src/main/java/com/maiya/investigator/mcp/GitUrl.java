package com.maiya.investigator.mcp;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GitUrl {
    private static final Pattern UNSAFE = Pattern.compile("[\\s;|&$`\\\\]|\\n|\\r");
    private static final Pattern SCP_SSH = Pattern.compile(
            "^[A-Za-z0-9._-]+@[A-Za-z0-9][A-Za-z0-9.-]*:[^\\s]+$"
    );

    private GitUrl() {
    }

    public static String normalize(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isBlank()) {
            throw new IllegalArgumentException("请填写 Git 仓库地址");
        }
        if (url.startsWith("-") || UNSAFE.matcher(url).find()) {
            throw new IllegalArgumentException("Git 地址包含非法字符");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")
                || lower.startsWith("ssh://") || lower.startsWith("file://")
                || SCP_SSH.matcher(url).matches()) {
            return url;
        }
        throw new IllegalArgumentException("Git 地址只支持 https / ssh:// / user@host:path / file");
    }

    public static String repositoryName(String url) {
        String cleaned = normalize(url).replaceFirst("(?i)\\.git$", "");
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf(':'));
        String name = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return sanitize(name);
    }

    public static String sanitize(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        if (cleaned.isBlank()) {
            return "repo";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    public static String withCredentials(String url, String username, String token) {
        String normalized = normalize(url);
        if (token == null || token.isBlank()) {
            return normalized;
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("用户名 / Token 仅用于 HTTPS 地址");
        }
        URI uri = URI.create(normalized);
        String user = username == null || username.isBlank() ? "git" : username.trim();
        String userinfo = URLEncoder.encode(user, StandardCharsets.UTF_8)
                + ":" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return uri.getScheme() + "://" + userinfo + "@" + uri.getRawAuthority() + uri.getRawPath() + query;
    }
}
