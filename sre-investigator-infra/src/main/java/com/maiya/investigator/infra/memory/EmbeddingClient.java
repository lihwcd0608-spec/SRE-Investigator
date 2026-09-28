package com.maiya.investigator.infra.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private final MemoryProperties.Qdrant qdrant;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public EmbeddingClient(MemoryProperties properties) {
        this.qdrant = properties.getQdrant();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.mapper = new ObjectMapper();
    }

    public boolean available() {
        return qdrant.getEmbeddingApiKey() != null && !qdrant.getEmbeddingApiKey().isBlank();
    }

    public List<Float> embed(String text) {
        if (!available() || text == null || text.isBlank()) {
            return List.of();
        }
        try {
            String body = mapper.createObjectNode()
                    .put("model", qdrant.getEmbeddingModel())
                    .put("input", text)
                    .put("dimensions", qdrant.getVectorSize())
                    .toString();
            String base = qdrant.getEmbeddingBaseUrl().endsWith("/")
                    ? qdrant.getEmbeddingBaseUrl().substring(0, qdrant.getEmbeddingBaseUrl().length() - 1)
                    : qdrant.getEmbeddingBaseUrl();
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/embeddings"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + qdrant.getEmbeddingApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("embedding 失败 status={}", response.statusCode());
                return List.of();
            }
            JsonNode data = mapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                return List.of();
            }
            List<Float> vector = new ArrayList<>();
            for (JsonNode item : data.get(0).path("embedding")) {
                vector.add((float) item.asDouble());
            }
            return vector;
        } catch (Exception exception) {
            log.warn("embedding 调用失败：{}", exception.getMessage());
            return List.of();
        }
    }
}
