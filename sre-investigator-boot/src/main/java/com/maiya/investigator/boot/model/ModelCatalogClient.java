package com.maiya.investigator.boot.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.graph.ChatModelFactory;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ModelCatalogClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient http;

    public ModelCatalogClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    public List<String> list(ResolvedModelSettings settings) {
        try {
            return switch (settings.provider()) {
                case OPENAI -> parseOpenAi(get(openAiModelsUrl(settings.baseUrl()), bearer(settings.apiKey()), null));
                case DASHSCOPE -> parseOpenAi(get(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
                        bearer(settings.apiKey()),
                        null
                ));
                case ANTHROPIC -> parseAnthropic(get(
                        "https://api.anthropic.com/v1/models",
                        null,
                        Map.of(
                                "x-api-key", settings.apiKey(),
                                "anthropic-version", "2023-06-01"
                        )
                ));
                case OLLAMA -> parseOllama(get(trim(settings.baseUrl()) + "/api/tags", null, null));
            };
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw new IllegalStateException("拉取模型列表失败：" + message, exception);
        }
    }

    public static String pickProbeModel(String requested, List<String> catalog, String implicitDefault) {
        String want = requested == null ? "" : requested.trim();
        List<String> models = catalog == null ? List.of() : catalog;
        if (!want.isBlank() && models.contains(want)) {
            return want;
        }
        if (!want.isBlank() && (implicitDefault == null || !want.equals(implicitDefault))) {
            return want;
        }
        if (!models.isEmpty()) {
            return models.get(0);
        }
        return want;
    }

    public static List<String> prepend(String model, List<String> catalog) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (model != null && !model.isBlank()) {
            ids.add(model.trim());
        }
        if (catalog != null) {
            ids.addAll(catalog);
        }
        return List.copyOf(ids);
    }

    static List<String> parseOpenAi(String body) {
        return collectIds(read(body).path("data"), "id");
    }

    static List<String> parseAnthropic(String body) {
        return collectIds(read(body).path("data"), "id");
    }

    static List<String> parseOllama(String body) {
        JsonNode models = read(body).path("models");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (models.isArray()) {
            for (JsonNode item : models) {
                add(ids, text(item, "name"));
                add(ids, text(item, "model"));
            }
        }
        return List.copyOf(ids);
    }

    private String get(String url, String authorization, Map<String, String> headers) {
        RestClient.RequestHeadersSpec<?> request = http.get().uri(url).accept(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        if (headers != null) {
            for (var entry : headers.entrySet()) {
                request = request.header(entry.getKey(), entry.getValue());
            }
        }
        String body = request.retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("模型列表为空");
        }
        return body;
    }

    private static String openAiModelsUrl(String baseUrl) {
        return ChatModelFactory.normalizeOpenAiBaseUrl(baseUrl) + "/v1/models";
    }

    private static String bearer(String apiKey) {
        return "Bearer " + apiKey;
    }

    private static String trim(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }

    private static JsonNode read(String body) {
        try {
            return JSON.readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException("模型列表不是合法 JSON", exception);
        }
    }

    private static List<String> collectIds(JsonNode array, String field) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                add(ids, text(item, field));
            }
        }
        return List.copyOf(ids);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static void add(LinkedHashSet<String> ids, String id) {
        if (id == null || id.isBlank() || !likelyChat(id)) {
            return;
        }
        ids.add(id.trim());
    }

    static boolean likelyChat(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        for (String token : List.of("embed", "whisper", "tts", "dall-e", "moderation", "realtime", "audio-")) {
            if (normalized.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
