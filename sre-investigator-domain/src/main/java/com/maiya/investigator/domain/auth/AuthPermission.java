package com.maiya.investigator.domain.auth;

import java.util.UUID;

public record AuthPermission(
        UUID id,
        String code,
        String name,
        String type,
        String menuPath,
        int sortOrder
) {
}
