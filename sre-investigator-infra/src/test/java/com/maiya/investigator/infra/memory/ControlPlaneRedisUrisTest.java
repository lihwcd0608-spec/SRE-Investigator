package com.maiya.investigator.infra.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.Test;

class ControlPlaneRedisUrisTest {
    @Test
    void sessionAndMemoryShareHostButUseOwnDatabase() {
        ControlPlaneRedisProperties redis = new ControlPlaneRedisProperties();
        redis.setHost("10.0.0.8");
        redis.setPort(6380);
        redis.setPassword("secret");
        redis.getSession().setDatabase(2);
        redis.getMemory().setDatabase(1);

        RedisURI session = ControlPlaneRedisUris.forSession(redis);
        RedisURI memory = ControlPlaneRedisUris.forMemory(redis);

        assertEquals("10.0.0.8", session.getHost());
        assertEquals(6380, session.getPort());
        assertEquals(2, session.getDatabase());
        assertEquals(1, memory.getDatabase());
        assertEquals("10.0.0.8", memory.getHost());
        assertEquals("secret", new String(session.getPassword()));
    }

    @Test
    void supportsUsernameAndPassword() {
        ControlPlaneRedisProperties redis = new ControlPlaneRedisProperties();
        redis.setHost("cache.example");
        redis.setUsername("sre");
        redis.setPassword("p@ss");
        redis.getMemory().setDatabase(1);

        RedisURI uri = ControlPlaneRedisUris.forMemory(redis);
        assertEquals("sre", uri.getUsername());
        assertEquals("p@ss", new String(uri.getPassword()));
        assertTrue(uri.getCredentialsProvider() != null || uri.getPassword() != null);
    }
}
