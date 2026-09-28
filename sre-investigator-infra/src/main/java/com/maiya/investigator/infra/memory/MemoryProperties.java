package com.maiya.investigator.infra.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.memory")
public class MemoryProperties {
    private int recentTurns = 2;
    private int summaryMaxChars = 600;
    private int ltmMaxChars = 800;
    private Qdrant qdrant = new Qdrant();

    public int getRecentTurns() {
        return recentTurns;
    }

    public void setRecentTurns(int recentTurns) {
        this.recentTurns = recentTurns;
    }

    public int getSummaryMaxChars() {
        return summaryMaxChars;
    }

    public void setSummaryMaxChars(int summaryMaxChars) {
        this.summaryMaxChars = summaryMaxChars;
    }

    public int getLtmMaxChars() {
        return ltmMaxChars;
    }

    public void setLtmMaxChars(int ltmMaxChars) {
        this.ltmMaxChars = ltmMaxChars;
    }

    public Qdrant getQdrant() {
        return qdrant;
    }

    public void setQdrant(Qdrant qdrant) {
        this.qdrant = qdrant == null ? new Qdrant() : qdrant;
    }

    public int recentMessageLimit() {
        return Math.max(1, recentTurns) * 2;
    }

    public static class Qdrant {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 6333;
        private String collection = "investigator_ltm";
        private String embeddingModel = "text-embedding-v3";
        private String embeddingBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String embeddingApiKey = "";
        private int vectorSize = 1024;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host == null ? "" : host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection == null ? "investigator_ltm" : collection;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel == null ? "" : embeddingModel;
        }

        public String getEmbeddingBaseUrl() {
            return embeddingBaseUrl;
        }

        public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
            this.embeddingBaseUrl = embeddingBaseUrl == null ? "" : embeddingBaseUrl;
        }

        public String getEmbeddingApiKey() {
            return embeddingApiKey;
        }

        public void setEmbeddingApiKey(String embeddingApiKey) {
            this.embeddingApiKey = embeddingApiKey == null ? "" : embeddingApiKey;
        }

        public int getVectorSize() {
            return vectorSize;
        }

        public void setVectorSize(int vectorSize) {
            this.vectorSize = vectorSize;
        }
    }
}
