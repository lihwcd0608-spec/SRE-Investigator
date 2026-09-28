package com.maiya.investigator.mcp.config;

import java.util.List;

public record ArmsEndpoint(String regionId, List<String> applications) {
    public ArmsEndpoint {
        regionId = regionId == null ? "" : regionId.trim();
        applications = applications == null ? List.of() : List.copyOf(applications);
    }

    public boolean configured() {
        return !regionId.isBlank();
    }

    public void requireAllowedApplication(String application) {
        if (!applications.isEmpty() && (application == null || applications.stream().noneMatch(application::equalsIgnoreCase))) {
            throw new IllegalArgumentException("ARMS Binding 未开放应用：" + application);
        }
    }

    public String summary() {
        return regionId + (applications.isEmpty() ? " / 全部应用" : " / " + applications.size() + " 个应用");
    }
}
