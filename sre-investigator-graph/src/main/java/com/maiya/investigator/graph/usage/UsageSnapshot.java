package com.maiya.investigator.graph.usage;

import java.util.List;
import java.util.UUID;

public record UsageSnapshot(UUID userId, String username, List<String> roleCodes, UUID projectId) {
    public UsageSnapshot {
        username = username == null ? "" : username;
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public String roleCodesCsv() {
        return String.join(",", roleCodes);
    }
}
