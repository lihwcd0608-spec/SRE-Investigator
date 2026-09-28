package com.maiya.investigator.infra.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maiya.investigator.domain.conversation.ConversationMessage;
import com.maiya.investigator.domain.memory.ConversationWindowCache;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisConversationWindowCache implements ConversationWindowCache {
    private static final Logger log = LoggerFactory.getLogger(RedisConversationWindowCache.class);
    private static final TypeReference<List<WindowMessage>> WINDOW = new TypeReference<>() {
    };

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final ObjectMapper mapper;
    private final int ttlSeconds;

    public RedisConversationWindowCache(RedisURI uri, int ttlSeconds) {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.ttlSeconds = Math.max(60, ttlSeconds);
        this.client = RedisClient.create(uri);
        this.connection = client.connect();
    }

    @Override
    public Optional<List<ConversationMessage>> get(UUID conversationId) {
        try {
            String json = commands().get(key(conversationId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<WindowMessage> rows = mapper.readValue(json, WINDOW);
            return Optional.of(rows.stream().map(WindowMessage::toDomain).toList());
        } catch (Exception exception) {
            log.warn("读取会话窗口失败，回退 MySQL：{}", exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID conversationId, List<ConversationMessage> messages) {
        try {
            List<WindowMessage> rows = messages == null ? List.of() : messages.stream().map(WindowMessage::from).toList();
            commands().setex(key(conversationId), ttlSeconds, mapper.writeValueAsString(rows));
        } catch (Exception exception) {
            log.warn("写入会话窗口失败：{}", exception.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        connection.close();
        client.shutdown();
    }

    private RedisCommands<String, String> commands() {
        return connection.sync();
    }

    private static String key(UUID conversationId) {
        return "conv:" + conversationId + ":window";
    }

    public static final class WindowMessage {
        public UUID id;
        public UUID conversationId;
        public String role;
        public String content;
        public UUID runId;
        public Instant createdAt;

        static WindowMessage from(ConversationMessage message) {
            WindowMessage row = new WindowMessage();
            row.id = message.id();
            row.conversationId = message.conversationId();
            row.role = message.role();
            row.content = message.content();
            row.runId = message.runId();
            row.createdAt = message.createdAt();
            return row;
        }

        ConversationMessage toDomain() {
            return new ConversationMessage(id, conversationId, role, content, runId, createdAt);
        }
    }
}
