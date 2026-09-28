package com.maiya.investigator.boot.auth;

import com.maiya.investigator.infra.memory.ControlPlaneRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 登录 Session 使用 spring.data.redis 里的连接 + session.database。
 */
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableConfigurationProperties(ControlPlaneRedisProperties.class)
public class ControlPlaneRedisConnectionConfig {
    @Bean
    LettuceConnectionFactory redisConnectionFactory(ControlPlaneRedisProperties redis) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                redis.getHost(),
                redis.getPort()
        );
        standalone.setDatabase(redis.getSession().getDatabase());
        applyAuth(standalone, redis);
        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder();
        if (redis.isSsl()) {
            client.useSsl();
        }
        return new LettuceConnectionFactory(standalone, client.build());
    }

    static void applyAuth(RedisStandaloneConfiguration standalone, ControlPlaneRedisProperties redis) {
        if (redis.getUsername() != null && !redis.getUsername().isBlank()) {
            standalone.setUsername(redis.getUsername());
        }
        if (redis.getPassword() != null && !redis.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(redis.getPassword()));
        }
    }
}
