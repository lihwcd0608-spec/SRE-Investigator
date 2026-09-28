package com.maiya.investigator.domain;

import java.time.Instant;
import java.util.UUID;

public record InvestigatorProject(
        UUID id,
        String name,
        String slug,
        String topology,
        Instant createdAt
) {
    public static InvestigatorProject create(String name, String slug, String topology) {
        return new InvestigatorProject(
                UUID.randomUUID(),
                name,
                slug,
                topology == null || topology.isBlank() ? "generic" : topology,
                Instant.now()
        );
    }

    public InvestigatorProject withDetails(String name, String slug, String topology) {
        return new InvestigatorProject(
                id,
                name,
                slug,
                topology == null || topology.isBlank() ? "generic" : topology,
                createdAt
        );
    }
}
