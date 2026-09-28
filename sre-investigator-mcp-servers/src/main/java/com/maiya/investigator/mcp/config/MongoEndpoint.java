package com.maiya.investigator.mcp.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record MongoEndpoint(
        String uri,
        String host,
        int port,
        String database,
        String username,
        String password
) {
    public boolean configured() {
        return notBlank(uri) || notBlank(host);
    }

    public String connectionUri() {
        if (notBlank(uri)) {
            return uri.trim();
        }
        String userinfo = "";
        if (notBlank(username)) {
            userinfo = encode(username);
            if (notBlank(password)) {
                userinfo += ":" + encode(password);
            }
            userinfo += "@";
        }
        String path = notBlank(database) ? "/" + database.trim() : "";
        return "mongodb://" + userinfo + host.trim() + ":" + (port > 0 ? port : 27017) + path;
    }

    public String defaultDatabase() {
        if (notBlank(database)) {
            return database.trim();
        }
        return databaseFromUri(connectionUri());
    }

    public String summary() {
        String target = mask(connectionUri());
        String db = defaultDatabase();
        if (notBlank(db) && !target.contains("/" + db)) {
            return target + "/" + db;
        }
        return target;
    }

    static String databaseFromUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String rest = raw.trim().replaceFirst("(?i)^mongodb(\\+srv)?://", "");
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1);
        }
        int slash = rest.indexOf('/');
        if (slash < 0 || slash == rest.length() - 1) {
            return "";
        }
        String path = rest.substring(slash + 1);
        int query = path.indexOf('?');
        return query >= 0 ? path.substring(0, query) : path;
    }

    static String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replaceFirst("(?i)(mongodb(?:\\+srv)?://[^:/@]+:)[^@/]+@", "$1***@");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
