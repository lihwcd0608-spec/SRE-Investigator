package com.maiya.investigator.mcp.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record SlsEndpoint(
        String regionId,
        String project,
        List<String> logstores,
        String accessKeyId,
        String accessKeySecret,
        String endpoint
) {
    public SlsEndpoint {
        regionId = trim(regionId);
        project = trim(project);
        accessKeyId = trim(accessKeyId);
        accessKeySecret = accessKeySecret == null ? "" : accessKeySecret;
        endpoint = trim(endpoint);
        logstores = normalizeLogstores(logstores);
    }

    public boolean configured() {
        return !regionId.isBlank()
                && !project.isBlank()
                && !accessKeyId.isBlank()
                && !accessKeySecret.isBlank();
    }

    public String host() {
        String base = endpoint.isBlank() ? regionId + ".log.aliyuncs.com" : stripScheme(endpoint);
        return project + "." + base;
    }

    public String defaultLogstore() {
        return logstores.isEmpty() ? "" : logstores.get(0);
    }

    public String resolveLogstore(String requested) {
        String value = trim(requested);
        if (value.isBlank()) {
            if (!logstores.isEmpty()) {
                // Binding 里填的是默认入口，不是白名单
                return logstores.get(0);
            }
            throw new IllegalArgumentException("未指定 logstore，且 Binding 也没有默认入口 logstore");
        }
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("logstore 只能包含字母、数字、下划线和连字符");
        }
        return value;
    }

    public String summary() {
        String stores = logstores.isEmpty() ? "*" : String.join(",", logstores);
        return regionId + "/" + project + "/" + stores;
    }

    private static List<String> normalizeLogstores(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> clean = new ArrayList<>();
        for (String item : values) {
            String value = trim(item);
            if (!value.isBlank() && !clean.contains(value)) {
                clean.add(value);
            }
        }
        return List.copyOf(clean);
    }

    private static String stripScheme(String value) {
        String trimmed = value.replaceFirst("(?i)^https?://", "");
        int slash = trimmed.indexOf('/');
        return slash >= 0 ? trimmed.substring(0, slash) : trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static List<String> parseLogstores(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(item -> item == null ? "" : String.valueOf(item).trim()).filter(item -> !item.isBlank()).toList();
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("[,，;；\\s]+")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    public static String normalizeRegion(String regionId) {
        return trim(regionId).toLowerCase(Locale.ROOT);
    }
}
