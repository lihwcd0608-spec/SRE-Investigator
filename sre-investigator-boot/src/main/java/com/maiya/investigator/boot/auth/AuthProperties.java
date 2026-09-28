package com.maiya.investigator.boot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.auth")
public class AuthProperties {
    private String bootstrapUser = "admin";
    private String bootstrapPassword = "";
    private final Feishu feishu = new Feishu();

    public String getBootstrapUser() {
        return bootstrapUser;
    }

    public void setBootstrapUser(String bootstrapUser) {
        this.bootstrapUser = bootstrapUser;
    }

    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }

    public Feishu getFeishu() {
        return feishu;
    }

    public static class Feishu {
        private String appId = "";
        private String appSecret = "";
        private String redirectUri = "http://localhost:8790/api/auth/feishu/callback";
        private String successRedirect = "/";
        private boolean botEnabled = false;
        private String verificationToken = "";
        private String encryptKey = "";
        private String serviceUsername = "feishu-bot";
        // 群聊按发送人隔离会话：同一人闲置超过该分钟数后，下次 @机器人 自动新开会话。<=0 表示不超时。
        private int sessionTtlMinutes = 60;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getSuccessRedirect() {
            return successRedirect;
        }

        public void setSuccessRedirect(String successRedirect) {
            this.successRedirect = successRedirect;
        }

        public boolean isBotEnabled() {
            return botEnabled;
        }

        public void setBotEnabled(boolean botEnabled) {
            this.botEnabled = botEnabled;
        }

        public String getVerificationToken() {
            return verificationToken;
        }

        public void setVerificationToken(String verificationToken) {
            this.verificationToken = verificationToken == null ? "" : verificationToken;
        }

        public String getEncryptKey() {
            return encryptKey;
        }

        public void setEncryptKey(String encryptKey) {
            this.encryptKey = encryptKey == null ? "" : encryptKey;
        }

        public String getServiceUsername() {
            return serviceUsername;
        }

        public void setServiceUsername(String serviceUsername) {
            this.serviceUsername = serviceUsername == null || serviceUsername.isBlank()
                    ? "feishu-bot"
                    : serviceUsername.trim();
        }

        public int getSessionTtlMinutes() {
            return sessionTtlMinutes;
        }

        public void setSessionTtlMinutes(int sessionTtlMinutes) {
            this.sessionTtlMinutes = sessionTtlMinutes;
        }

        public boolean configured() {
            return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
        }

        public boolean botConfigured() {
            return configured() && botEnabled;
        }
    }
}
