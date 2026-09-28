package com.maiya.investigator.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.repos")
public class RepositoryWorkspaceProperties {
    private int timeoutSeconds = 180;
    private String defaultName = "demo-app-abroad";

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.min(Math.max(timeoutSeconds, 10), 600);
    }

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName == null || defaultName.isBlank() ? "demo-app-abroad" : defaultName.trim();
    }
}
