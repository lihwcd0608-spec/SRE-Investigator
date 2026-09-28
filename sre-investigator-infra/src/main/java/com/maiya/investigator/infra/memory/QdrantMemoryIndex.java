package com.maiya.investigator.infra.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maiya.investigator.domain.memory.MemoryFact;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QdrantMemoryIndex {
    private static final Logger log = LoggerFactory.getLogger(QdrantMemoryIndex.class);
    private final MemoryProperties.Qdrant qdrant;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private volatile boolean collectionReady;

    public QdrantMemoryIndex(MemoryProperties properties) {
        this.qdrant = properties.getQdrant();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.mapper = new ObjectMapper();
    }

    public boolean enabled() {
        return qdrant.isEnabled();
    }

    public void upsert(MemoryFact fact, List<Float> vector) {
        if (!enabled() || fact == null || vector == null || vector.isEmpty()) {
            return;
        }
        try {
            ensureCollection(vector.size());
            ObjectNode point = mapper.createObjectNode();
            point.put("id", fact.id().toString());
            ArrayNode values = point.putArray("vector");
            vector.forEach(values::add);
            ObjectNode payload = point.putObject("payload");
            payload.put("projectId", fact.projectId().toString());
            payload.put("kind", fact.kind());
            payload.put("factId", fact.id().toString());
            payload.put("text", fact.content());
            ObjectNode body = mapper.createObjectNode();
            body.putArray("points").add(point);
            HttpResponse<String> response = send("PUT", "/collections/" + qdrant.getCollection() + "/points?wait=true", body.toString());
            if (response.statusCode() >= 300) {
                log.warn("Qdrant upsert 失败 status={}", response.statusCode());
            }
        } catch (Exception exception) {
            log.warn("Qdrant upsert 失败：{}", exception.getMessage());
        }
    }

    public List<String> search(UUID projectId, List<Float> vector, int limit) {
        if (!enabled() || projectId == null || vector == null || vector.isEmpty()) {
            return List.of();
        }
        try {
            ensureCollection(vector.size());
            ObjectNode body = mapper.createObjectNode();
            ArrayNode values = body.putArray("vector");
            vector.forEach(values::add);
            body.put("limit", Math.max(1, limit));
            body.put("with_payload", true);
            ObjectNode filter = body.putObject("filter");
            ObjectNode match = mapper.createObjectNode();
            match.put("key", "projectId");
            match.putObject("match").put("value", projectId.toString());
            filter.putArray("must").add(match);
            HttpResponse<String> response = send("POST", "/collections/" + qdrant.getCollection() + "/points/search", body.toString());
            if (response.statusCode() >= 300) {
                log.warn("Qdrant 检索失败 status={}", response.statusCode());
                return List.of();
            }
            JsonNode result = mapper.readTree(response.body()).path("result");
            List<String> hits = new ArrayList<>();
            for (JsonNode item : result) {
                String text = item.path("payload").path("text").asText("");
                if (!text.isBlank()) {
                    hits.add(text);
                }
            }
            return hits;
        } catch (Exception exception) {
            log.warn("Qdrant 检索失败：{}", exception.getMessage());
            return List.of();
        }
    }

    private void ensureCollection(int vectorSize) throws Exception {
        if (collectionReady) {
            return;
        }
        ObjectNode body = mapper.createObjectNode();
        ObjectNode vectors = body.putObject("vectors");
        vectors.put("size", vectorSize);
        vectors.put("distance", "Cosine");
        HttpResponse<String> response = send("PUT", "/collections/" + qdrant.getCollection(), body.toString());
        if (response.statusCode() < 300 || response.statusCode() == 409) {
            collectionReady = true;
            return;
        }
        log.warn("创建 Qdrant collection 失败 status={}", response.statusCode());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://" + qdrant.getHost() + ":" + qdrant.getPort() + path))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json");
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
