package com.maiya.investigator.domain.control;

public record CatalogTemplate(
        String id,
        CatalogKind kind,
        CatalogSource source,
        String name,
        String description,
        String origin,
        String packageName,
        String sourceUrl,
        String gitRef,
        String command,
        String args,
        String route,
        String toolKind,
        String content
) {
}
