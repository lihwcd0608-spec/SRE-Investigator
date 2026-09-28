package com.maiya.investigator.mcp.config;

public record MseEndpoint(
        String regionId,
        String accessKeyId,
        String accessKeySecret,
        String gatewayUniqueId,
        String nacosInstanceId,
        String endpoint
) {
    public MseEndpoint {
        regionId = trim(regionId);
        accessKeyId = trim(accessKeyId);
        accessKeySecret = accessKeySecret == null ? "" : accessKeySecret;
        gatewayUniqueId = trim(gatewayUniqueId);
        nacosInstanceId = trim(nacosInstanceId);
        endpoint = trim(endpoint);
    }

    public boolean configured() {
        return !regionId.isBlank() && !accessKeyId.isBlank() && !accessKeySecret.isBlank();
    }

    public String host() {
        if (!endpoint.isBlank()) {
            return stripScheme(endpoint);
        }
        return "mse." + regionId + ".aliyuncs.com";
    }

    public String requireGatewayId(String requested) {
        String value = first(requested, gatewayUniqueId);
        if (value.isBlank()) {
            throw new IllegalArgumentException("MSE 网关工具需要 gatewayUniqueId，请在 Binding 或参数里填写");
        }
        return value;
    }

    public String requireNacosInstanceId(String requested) {
        String value = first(requested, nacosInstanceId);
        if (value.isBlank()) {
            throw new IllegalArgumentException("MSE Nacos 工具需要 nacosInstanceId，请在 Binding 或参数里填写");
        }
        return value;
    }

    public String summary() {
        StringBuilder text = new StringBuilder(regionId);
        if (!gatewayUniqueId.isBlank()) {
            text.append(" gw=").append(gatewayUniqueId);
        }
        if (!nacosInstanceId.isBlank()) {
            text.append(" nacos=").append(nacosInstanceId);
        }
        return text.toString();
    }

    private static String first(String requested, String fallback) {
        String value = trim(requested);
        return value.isBlank() ? fallback : value;
    }

    private static String stripScheme(String value) {
        String trimmed = value.replaceFirst("(?i)^https?://", "");
        int slash = trimmed.indexOf('/');
        return slash >= 0 ? trimmed.substring(0, slash) : trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
