package com.maiya.investigator.boot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.DefaultCookieSerializer;

class RedisSessionConfigTest {
    private final RedisSessionConfig config = new RedisSessionConfig();

    @Test
    void cookieKeepsLoginFor24Hours() {
        DefaultCookieSerializer serializer = (DefaultCookieSerializer) config.cookieSerializer();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        serializer.writeCookieValue(new DefaultCookieSerializer.CookieValue(request, response, "sid"));
        Cookie cookie = response.getCookie("INVESTIGATOR_SESSION");
        assertEquals("INVESTIGATOR_SESSION", cookie.getName());
        assertEquals(RedisSessionConfig.SESSION_SECONDS, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    void redisDownFailsStartup() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("Connection refused"));
        ApplicationRunner runner = config.redisSessionReadyCheck(factory);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments())
        );
        assertTrue(ex.getMessage().contains("Redis 连不上"));
        assertTrue(ex.getMessage().contains(RedisSessionConfig.DEFAULT_REDIS_URI));
    }

    @Test
    void redisPingSucceeds() throws Exception {
        RedisConnection connection = mock(RedisConnection.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        config.redisSessionReadyCheck(factory).run(new DefaultApplicationArguments());
    }
}
