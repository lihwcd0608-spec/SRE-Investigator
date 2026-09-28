package com.maiya.investigator.domain.model;

public record ResolvedModelSettings(
        ModelProvider provider,
        String model,
        String apiKey,
        String baseUrl,
        boolean enabled
) {
    public static ResolvedModelSettings disabled() {
        return new ResolvedModelSettings(ModelProvider.DASHSCOPE, "", "", "", false);
    }

    public ResolvedModelSettings withModel(String nextModel) {
        return new ResolvedModelSettings(provider, nextModel == null ? "" : nextModel, apiKey, baseUrl, enabled);
    }

    public boolean credentialsReady() {
        if (!enabled) {
            return false;
        }
        if (provider.needsApiKey() && (apiKey == null || apiKey.isBlank())) {
            return false;
        }
        return !provider.needsBaseUrl() || (baseUrl != null && !baseUrl.isBlank());
    }

    public boolean available() {
        return credentialsReady() && model != null && !model.isBlank();
    }

    public String missingReason() {
        if (!enabled) {
            return "模型已关闭（no-dashscope 或 investigator.model.enabled=false）。";
        }
        if (provider.needsApiKey() && (apiKey == null || apiKey.isBlank())) {
            return "未配置 " + provider.label() + " 的 API Key。";
        }
        if (provider.needsBaseUrl() && (baseUrl == null || baseUrl.isBlank())) {
            return "未配置 " + provider.label() + " 的 Base URL。";
        }
        if (model == null || model.isBlank()) {
            return "还没有可用模型。请先点「测试连接」拉取服务方列表，或手动填写模型名。";
        }
        return "当前供应商 " + provider.label() + " 尚未就绪。";
    }
}
