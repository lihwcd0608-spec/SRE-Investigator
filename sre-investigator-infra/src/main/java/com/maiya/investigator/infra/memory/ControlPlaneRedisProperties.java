package com.maiya.investigator.infra.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制面 Redis：连接只写一处，Session / 记忆用各自的 database。
 * 对应 yaml：spring.data.redis.session.database / spring.data.redis.memory.database
 */
@ConfigurationProperties(prefix = "spring.data.redis")
public class ControlPlaneRedisProperties {
    private String host = "127.0.0.1";
    private int port = 6379;
    private String username = "";
    private String password = "";
    private boolean ssl = false;
    private Session session = new Session();
    private Memory memory = new Memory();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port <= 0 ? 6379 : port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username.trim();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session == null ? new Session() : session;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory == null ? new Memory() : memory;
    }

    public static class Session {
        private int database = 2;

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = Math.max(0, database);
        }
    }

    public static class Memory {
        private boolean enabled = false;
        private int database = 1;
        private int ttlSeconds = 7200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = Math.max(0, database);
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }
}
