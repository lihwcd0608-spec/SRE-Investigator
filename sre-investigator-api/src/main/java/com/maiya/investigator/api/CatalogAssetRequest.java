package com.maiya.investigator.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CatalogAssetRequest(
        @NotBlank String kind,
        String source,
        String slug,
        @NotBlank String name,
        String route,
        Boolean enabled,
        String origin,
        String catalogId,
        String packageName,
        String sourceUrl,
        String gitRef,
        String command,
        String args,
        String content,
        String summary,
        List<String> tags,
        List<CatalogAssetFileRequest> files
) {
}
