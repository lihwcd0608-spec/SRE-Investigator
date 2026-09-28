package com.maiya.investigator.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.aliyun")
public class AliyunCloudProperties {
    private String accessKeyId = "";
    private String accessKeySecret = "";

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId == null ? "" : accessKeyId.trim();
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret == null ? "" : accessKeySecret.trim();
    }

    public boolean configured() {
        return !accessKeyId.isBlank() && !accessKeySecret.isBlank();
    }
}
