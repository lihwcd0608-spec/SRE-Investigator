package com.maiya.investigator.graph;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.domain.usage.ModelUsageSink;
import com.maiya.investigator.graph.usage.UsageRecordingChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ChatModelFactory {
    private final ObjectProvider<ModelUsageSink> usageSinks;

    public ChatModelFactory(ObjectProvider<ModelUsageSink> usageSinks) {
        this.usageSinks = usageSinks;
    }

    public ChatModel create(ResolvedModelSettings settings) {
        if (settings == null || !settings.available()) {
            throw new IllegalStateException(settings == null ? "未配置模型" : settings.missingReason());
        }
        ChatModel model = switch (settings.provider()) {
            case DASHSCOPE -> dashscope(settings);
            case OPENAI -> openai(settings);
            case ANTHROPIC -> anthropic(settings);
            case OLLAMA -> ollama(settings);
        };
        return wrap(model, settings);
    }

    private ChatModel wrap(ChatModel model, ResolvedModelSettings settings) {
        ModelUsageSink sink = usageSinks == null ? null : usageSinks.getIfAvailable();
        if (sink == null) {
            return model;
        }
        return new UsageRecordingChatModel(model, settings, sink);
    }

    private static ChatModel dashscope(ResolvedModelSettings settings) {
        DashScopeApi api = DashScopeApi.builder().apiKey(settings.apiKey()).build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(settings.model())
                .withTemperature(0.2)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(options)
                .build();
    }

    private static ChatModel openai(ResolvedModelSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(settings.apiKey())
                .baseUrl(normalizeOpenAiBaseUrl(settings.baseUrl()))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(settings.model())
                        .temperature(0.2)
                        .build())
                .build();
    }

    private static ChatModel anthropic(ResolvedModelSettings settings) {
        AnthropicApi api = AnthropicApi.builder().apiKey(settings.apiKey()).build();
        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(settings.model())
                        .temperature(0.2)
                        .maxTokens(4096)
                        .build())
                .build();
    }

    private static ChatModel ollama(ResolvedModelSettings settings) {
        OllamaApi api = OllamaApi.builder().baseUrl(settings.baseUrl()).build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(settings.model())
                        .temperature(0.2)
                        .build())
                .build();
    }

    public static String normalizeOpenAiBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return ModelOpenAiDefaults.BASE_URL;
        }
        String trimmed = baseUrl.trim().replaceAll("/+$", "");
        if (trimmed.endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }

    private static final class ModelOpenAiDefaults {
        private static final String BASE_URL = "https://api.openai.com";
    }
}
