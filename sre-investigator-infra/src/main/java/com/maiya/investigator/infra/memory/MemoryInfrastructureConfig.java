package com.maiya.investigator.infra.memory;

import com.maiya.investigator.domain.memory.ConversationWindowCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MemoryProperties.class, ControlPlaneRedisProperties.class})
public class MemoryInfrastructureConfig {
    private static final Logger log = LoggerFactory.getLogger(MemoryInfrastructureConfig.class);

    @Bean
    ConversationWindowCache conversationWindowCache(
            MemoryProperties properties,
            ControlPlaneRedisProperties redis
    ) {
        if (!redis.getMemory().isEnabled()) {
            return ConversationWindowCache.noop();
        }
        try {
            return new RedisConversationWindowCache(
                    ControlPlaneRedisUris.forMemory(redis),
                    redis.getMemory().getTtlSeconds()
            );
        } catch (RuntimeException exception) {
            log.warn("控制面 Redis 不可用，STM 回退 MySQL：{}", exception.getMessage());
            return ConversationWindowCache.noop();
        }
    }

    @Bean
    EmbeddingClient embeddingClient(MemoryProperties properties) {
        return new EmbeddingClient(properties);
    }

    @Bean
    QdrantMemoryIndex qdrantMemoryIndex(MemoryProperties properties) {
        return new QdrantMemoryIndex(properties);
    }
}
