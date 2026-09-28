package com.maiya.investigator.domain.auth;

import java.util.UUID;

public record AuthRole(
        UUID id,
        String code,
        String name,
        boolean builtin,
        String description
) {
}
