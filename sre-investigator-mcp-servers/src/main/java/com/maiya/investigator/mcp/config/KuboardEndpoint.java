package com.maiya.investigator.mcp.config;

public record KuboardEndpoint(
        String baseUrl,
        String username,
        String password,
        String cluster,
        String namespace,
        boolean insecureSkipTls
) {
    public KuboardEndpoint {
        baseUrl = normalizeBase(baseUrl);
        username = trim(username);
        password = password == null ? "" : password;
        cluster = trim(cluster);
        namespace = trim(namespace);
    }

    public boolean credentialsReady() {
        return !baseUrl.isBlank() && !username.isBlank() && !password.isBlank();
    }

    public boolean configured() {
        return credentialsReady() && !cluster.isBlank();
    }

    public KuboardEndpoint withCluster(String name) {
        return new KuboardEndpoint(baseUrl, username, password, name, namespace, insecureSkipTls);
    }

    public String summary() {
        String ns = namespace.isBlank() ? "*" : namespace;
        return host() + " cluster=" + cluster + " ns=" + ns;
    }

    public String host() {
        return baseUrl.replaceFirst("(?i)^https?://", "");
    }

    public String origin() {
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return baseUrl;
            }
            String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (RuntimeException exception) {
            return baseUrl;
        }
    }

    public String resolveNamespace(String requested) {
        String value = trim(requested);
        if (!value.isBlank()) {
            requireResourceName(value, "namespace");
            return value;
        }
        if (!namespace.isBlank()) {
            return namespace;
        }
        throw new IllegalArgumentException("未指定 namespace，且 Binding 也没有默认命名空间");
    }

    public static void requireResourceName(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException(field + " 只能包含字母、数字、点、下划线和连字符");
        }
    }

    private static String normalizeBase(String value) {
        String trimmed = trim(value);
        int hash = trimmed.indexOf('#');
        if (hash >= 0) {
            trimmed = trimmed.substring(0, hash);
        }
        trimmed = trimmed.replaceFirst("(?i)/kubernetes(/[^/?#]+.*)?$", "");
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
