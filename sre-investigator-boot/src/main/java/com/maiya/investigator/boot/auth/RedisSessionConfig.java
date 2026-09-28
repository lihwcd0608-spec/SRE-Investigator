package com.maiya.investigator.boot.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = RedisSessionConfig.SESSION_SECONDS, redisNamespace = "investigator:session")
public class RedisSessionConfig {
    static final int SESSION_SECONDS = 24 * 60 * 60;
    static final String DEFAULT_REDIS_URI = "redis://127.0.0.1:6379/2";
    private static final Logger log = LoggerFactory.getLogger(RedisSessionConfig.class);

    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("INVESTIGATOR_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge(SESSION_SECONDS);
        return serializer;
    }

    @Bean
    ApplicationRunner redisSessionReadyCheck(RedisConnectionFactory factory) {
        return args -> {
            try (RedisConnection connection = factory.getConnection()) {
                connection.ping();
            } catch (Exception ex) {
                log.error("登录 Session 依赖的 Redis 连不上，无法启动。请检查 spring.data.redis（默认 {}）", DEFAULT_REDIS_URI);
                throw new IllegalStateException("登录 Session 依赖的 Redis 连不上（默认 " + DEFAULT_REDIS_URI + "）", ex);
            }
            log.info("登录 Session 已接入 Redis，空闲 24 小时滑动过期");
        };
    }
}
