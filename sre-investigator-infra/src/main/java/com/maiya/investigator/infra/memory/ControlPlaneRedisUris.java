package com.maiya.investigator.infra.memory;

import io.lettuce.core.RedisURI;

/**
 * Session 与记忆共用同一套 Redis 连接（含账号密码），只用 database 区分库号。
 */
public final class ControlPlaneRedisUris {
    private ControlPlaneRedisUris() {
    }

    public static RedisURI forSession(ControlPlaneRedisProperties redis) {
        ControlPlaneRedisProperties source = redis == null ? new ControlPlaneRedisProperties() : redis;
        return forDatabase(source, source.getSession().getDatabase());
    }

    public static RedisURI forMemory(ControlPlaneRedisProperties redis) {
        ControlPlaneRedisProperties source = redis == null ? new ControlPlaneRedisProperties() : redis;
        return forDatabase(source, source.getMemory().getDatabase());
    }

    public static RedisURI forDatabase(ControlPlaneRedisProperties redis, int database) {
        ControlPlaneRedisProperties source = redis == null ? new ControlPlaneRedisProperties() : redis;
        int db = Math.max(0, database);
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(source.getHost())
                .withPort(source.getPort())
                .withDatabase(db);
        applyAuth(builder, source);
        if (source.isSsl()) {
            builder.withSsl(true);
        }
        return builder.build();
    }

    static void applyAuth(RedisURI.Builder builder, ControlPlaneRedisProperties redis) {
        String username = redis.getUsername();
        String password = redis.getPassword();
        if (username != null && !username.isBlank()) {
            builder.withAuthentication(
                    username,
                    password == null ? new char[0] : password.toCharArray()
            );
            return;
        }
        if (password != null && !password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }
    }
}
