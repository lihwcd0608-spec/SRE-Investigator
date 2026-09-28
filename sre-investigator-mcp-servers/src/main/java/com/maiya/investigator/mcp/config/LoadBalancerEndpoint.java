package com.maiya.investigator.mcp.config;

import java.util.List;

public record LoadBalancerEndpoint(String regionId, String serviceType, List<String> instanceIds) {
    public LoadBalancerEndpoint {
        regionId = regionId == null ? "" : regionId.trim();
        serviceType = serviceType == null || serviceType.isBlank() ? "ALB" : serviceType.trim().toUpperCase();
        instanceIds = instanceIds == null ? List.of() : List.copyOf(instanceIds);
        if (!"ALB".equals(serviceType) && !"CLB".equals(serviceType)) {
            throw new IllegalArgumentException("负载均衡类型只支持 ALB 或 CLB");
        }
    }

    public boolean configured() {
        return !regionId.isBlank();
    }

    public void requireAllowedInstance(String instanceId) {
        if (!instanceIds.isEmpty() && (instanceId == null || !instanceIds.contains(instanceId))) {
            throw new IllegalArgumentException("负载均衡 Binding 未开放实例：" + instanceId);
        }
    }

    public String summary() {
        return serviceType + " / " + regionId
                + (instanceIds.isEmpty() ? " / 全部实例" : " / " + instanceIds.size() + " 个实例");
    }
}
