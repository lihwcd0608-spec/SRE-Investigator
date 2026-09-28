package com.maiya.investigator.domain.control;

import java.time.Instant;
import java.util.UUID;

public record CatalogAsset(
        UUID id,
        UUID projectId,
        CatalogKind kind,
        CatalogSource source,
        String slug,
        String name,
        String route,
        boolean enabled,
        String origin,
        String catalogId,
        String packageName,
        String sourceUrl,
        String gitRef,
        String command,
        String args,
        String content,
        String summary,
        String tags,
        Instant createdAt,
        Instant updatedAt
) {
    public static CatalogAsset create(
            UUID projectId,
            CatalogKind kind,
            CatalogSource source,
            String slug,
            String name,
            Instant now
    ) {
        return new CatalogAsset(
                UUID.randomUUID(),
                projectId,
                kind,
                source,
                slug,
                name,
                null,
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    public CatalogAsset withEnabled(boolean enabled, Instant updatedAt) {
        return new CatalogAsset(
                id, projectId, kind, source, slug, name, route, enabled, origin, catalogId,
                packageName, sourceUrl, gitRef, command, args, content, summary, tags, createdAt, updatedAt
        );
    }

    public CatalogAsset withDetails(
            CatalogSource source,
            String slug,
            String name,
            String route,
            String origin,
            String catalogId,
            String packageName,
            String sourceUrl,
            String gitRef,
            String command,
            String args,
            String content,
            String summary,
            String tags,
            Instant updatedAt
    ) {
        return new CatalogAsset(
                id, projectId, kind, source, slug, name, route, enabled, origin, catalogId,
                packageName, sourceUrl, gitRef, command, args, content, summary, tags, createdAt, updatedAt
        );
    }
}
