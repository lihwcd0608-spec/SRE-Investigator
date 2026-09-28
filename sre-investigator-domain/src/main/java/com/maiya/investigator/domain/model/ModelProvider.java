package com.maiya.investigator.domain.model;

import java.util.Arrays;
import java.util.Locale;

public enum ModelProvider {
    DASHSCOPE("阿里百炼", true, false, "qwen-plus", ""),
    OPENAI("OpenAI 兼容", true, true, "gpt-4o-mini", "https://api.openai.com"),
    ANTHROPIC("Anthropic", true, false, "claude-sonnet-4-20250514", ""),
    OLLAMA("Ollama", false, true, "qwen2.5", "http://localhost:11434");

    private final String label;
    private final boolean needsApiKey;
    private final boolean needsBaseUrl;
    private final String defaultModel;
    private final String defaultBaseUrl;

    ModelProvider(String label, boolean needsApiKey, boolean needsBaseUrl, String defaultModel, String defaultBaseUrl) {
        this.label = label;
        this.needsApiKey = needsApiKey;
        this.needsBaseUrl = needsBaseUrl;
        this.defaultModel = defaultModel;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String label() {
        return label;
    }

    public boolean needsApiKey() {
        return needsApiKey;
    }

    public boolean needsBaseUrl() {
        return needsBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ModelProvider from(String value) {
        if (value == null || value.isBlank()) {
            return DASHSCOPE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(item -> item.id().equals(normalized) || item.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的模型供应商：" + value));
    }
}
