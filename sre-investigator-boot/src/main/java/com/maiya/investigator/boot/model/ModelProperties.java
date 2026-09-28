package com.maiya.investigator.boot.model;

import com.maiya.investigator.domain.model.ModelProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investigator.model")
public class ModelProperties {
    private boolean enabled = true;
    private String provider = ModelProvider.DASHSCOPE.id();
    private Provider dashscope = new Provider();
    private Provider openai = new Provider();
    private Provider anthropic = new Provider();
    private Provider ollama = new Provider();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Provider getDashscope() {
        return dashscope;
    }

    public void setDashscope(Provider dashscope) {
        this.dashscope = dashscope == null ? new Provider() : dashscope;
    }

    public Provider getOpenai() {
        return openai;
    }

    public void setOpenai(Provider openai) {
        this.openai = openai == null ? new Provider() : openai;
    }

    public Provider getAnthropic() {
        return anthropic;
    }

    public void setAnthropic(Provider anthropic) {
        this.anthropic = anthropic == null ? new Provider() : anthropic;
    }

    public Provider getOllama() {
        return ollama;
    }

    public void setOllama(Provider ollama) {
        this.ollama = ollama == null ? new Provider() : ollama;
    }

    public Provider provider(ModelProvider type) {
        return switch (type) {
            case DASHSCOPE -> dashscope;
            case OPENAI -> openai;
            case ANTHROPIC -> anthropic;
            case OLLAMA -> ollama;
        };
    }

    public static class Provider {
        private String apiKey = "";
        private String baseUrl = "";
        private String model = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? "" : baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model == null ? "" : model;
        }
    }
}
