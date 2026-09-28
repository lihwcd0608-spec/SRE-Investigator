package com.maiya.investigator.mcp;

import java.util.List;
import java.util.Locale;

public final class ProfileNames {
    private ProfileNames() {
    }

    public static String resolve(String requested, List<String> allowed) {
        return resolve(requested, allowed, "工具");
    }

    public static String resolve(String requested, List<String> allowed, String tool) {
        if (allowed == null || allowed.isEmpty()) {
            throw new IllegalArgumentException(tool + " 未绑定到当前项目环境");
        }
        if (requested == null || requested.isBlank()) {
            return allowed.get(0);
        }
        String want = requested.trim();
        for (String name : allowed) {
            if (name.equalsIgnoreCase(want)) {
                return name;
            }
        }
        String normalized = normalize(want);
        for (String name : allowed) {
            if (normalize(name).equals(normalized)) {
                return name;
            }
        }
        if (allowed.size() == 1) {
            return allowed.get(0);
        }
        throw new IllegalArgumentException(
                tool + " profile 未绑定到当前项目环境：" + requested + "；可用：" + String.join(", ", allowed)
        );
    }

    public static String resolveExact(String requested, List<String> allowed, String tool) {
        if (allowed == null || allowed.isEmpty()) {
            throw new IllegalArgumentException(tool + " 未配置：" + requested);
        }
        if (requested == null || requested.isBlank()) {
            return allowed.get(0);
        }
        String want = requested.trim();
        for (String name : allowed) {
            if (name.equalsIgnoreCase(want) || normalize(name).equals(normalize(want))) {
                return name;
            }
        }
        throw new IllegalArgumentException(tool + " profile 未配置：" + requested + "；可用：" + String.join(", ", allowed));
    }

    static String normalize(String value) {
        String trimmed = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        while (trimmed.startsWith("mysql-") || trimmed.startsWith("redis-") || trimmed.startsWith("mcp-")) {
            trimmed = trimmed.substring(trimmed.indexOf('-') + 1);
        }
        return trimmed;
    }
}
