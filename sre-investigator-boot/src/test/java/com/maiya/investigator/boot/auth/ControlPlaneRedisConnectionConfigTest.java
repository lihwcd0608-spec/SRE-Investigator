package com.maiya.investigator.boot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.infra.memory.ControlPlaneRedisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

class ControlPlaneRedisConnectionConfigTest {
    @Test
    void appliesUsernameAndPassword() {
        ControlPlaneRedisProperties redis = new ControlPlaneRedisProperties();
        redis.setUsername("sre");
        redis.setPassword("secret");
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        ControlPlaneRedisConnectionConfig.applyAuth(standalone, redis);
        assertEquals("sre", standalone.getUsername());
        assertTrue(standalone.getPassword().isPresent());
        assertEquals("secret", new String(standalone.getPassword().get()));
    }

    @Test
    void blankAuthLeftUnset() {
        ControlPlaneRedisProperties redis = new ControlPlaneRedisProperties();
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        ControlPlaneRedisConnectionConfig.applyAuth(standalone, redis);
        assertTrue(standalone.getUsername() == null || standalone.getUsername().isBlank());
        assertTrue(standalone.getPassword() == null || !standalone.getPassword().isPresent());
    }
}
