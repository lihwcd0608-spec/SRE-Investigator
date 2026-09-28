package com.maiya.investigator.mcp.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.tools")
public class ReadonlyToolProperties {
    private final Map<String, MysqlProfile> mysqlProfiles = new LinkedHashMap<>();
    private final Map<String, String> redisProfiles = new LinkedHashMap<>();
    private String defaultProfile = "test";
    private String redisUri;
    private String mysqlUri = "";
    private int maxRows = 200;

    public Map<String, MysqlProfile> getMysqlProfiles() {
        return mysqlProfiles;
    }

    public Map<String, String> getRedisProfiles() {
        return redisProfiles;
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public String getRedisUri() {
        return redisUri;
    }

    public void setRedisUri(String redisUri) {
        this.redisUri = redisUri;
    }

    public String getMysqlUri() {
        return mysqlUri;
    }

    public void setMysqlUri(String mysqlUri) {
        this.mysqlUri = mysqlUri == null ? "" : mysqlUri;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = Math.min(Math.max(maxRows, 1), 200);
    }

    public static class MysqlProfile {
        private String host = "";
        private int port = 3306;
        private String database = "";
        private String username = "";
        private String password = "";
        private String parameters = "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";

        public boolean configured() {
            return notBlank(host) && notBlank(database) && notBlank(username);
        }

        public String jdbcUrl() {
            String params = notBlank(parameters)
                    ? parameters
                    : "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
            return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params;
        }

        public String mysqlUri() {
            return "mysql://" + nullToEmpty(username) + ":" + nullToEmpty(password)
                    + "@" + host + ":" + port + "/" + database;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host == null ? "" : host.trim();
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port > 0 ? port : 3306;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database == null ? "" : database.trim();
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username == null ? "" : username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password == null ? "" : password;
        }

        public String getParameters() {
            return parameters;
        }

        public void setParameters(String parameters) {
            this.parameters = parameters == null ? "" : parameters;
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
