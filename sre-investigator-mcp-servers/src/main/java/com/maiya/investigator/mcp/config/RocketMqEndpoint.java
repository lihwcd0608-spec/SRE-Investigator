package com.maiya.investigator.mcp.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record RocketMqEndpoint(
        String namesrvAddr,
        String accessKey,
        String secretKey,
        String cluster,
        String namespace
) {
    public boolean configured() {
        return namesrvAddr != null && !namesrvAddr.isBlank();
    }

    public boolean aclEnabled() {
        return accessKey != null && !accessKey.isBlank();
    }

    public String summary() {
        StringBuilder text = new StringBuilder(namesrvAddr);
        if (cluster != null && !cluster.isBlank()) {
            text.append(" cluster=").append(cluster);
        }
        if (namespace != null && !namespace.isBlank()) {
            text.append(" ns=").append(namespace);
        }
        return text.toString();
    }

    public Map<String, Object> safeView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("namesrvAddr", namesrvAddr);
        view.put("cluster", cluster);
        view.put("namespace", namespace);
        view.put("acl", aclEnabled());
        return view;
    }
}
