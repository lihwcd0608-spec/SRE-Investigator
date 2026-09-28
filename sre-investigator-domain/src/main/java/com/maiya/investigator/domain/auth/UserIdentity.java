package com.maiya.investigator.domain.auth;

import java.util.UUID;

public record UserIdentity(
        UUID id,
        UUID userId,
        String provider,
        String externalId
) {
    public static final String PASSWORD = "PASSWORD";
    public static final String FEISHU = "FEISHU";
}
