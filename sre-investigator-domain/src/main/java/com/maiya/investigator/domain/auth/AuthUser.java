package com.maiya.investigator.domain.auth;

import java.time.Instant;
import java.util.UUID;

public record AuthUser(
        UUID id,
        String username,
        String displayName,
        String email,
        String passwordHash,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    public boolean enabled() {
        return ENABLED.equals(status);
    }

    public AuthUser withPasswordHash(String passwordHash, Instant updatedAt) {
        return new AuthUser(id, username, displayName, email, passwordHash, status, createdAt, updatedAt);
    }

    public AuthUser withDetails(String displayName, String email, String status, Instant updatedAt) {
        return new AuthUser(id, username, displayName, email, passwordHash, status, createdAt, updatedAt);
    }
}
