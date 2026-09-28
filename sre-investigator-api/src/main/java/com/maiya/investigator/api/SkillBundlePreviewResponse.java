package com.maiya.investigator.api;

import java.util.List;

public record SkillBundlePreviewResponse(
        String name,
        String summary,
        String entryPath,
        List<CatalogAssetFileResponse> files
) {
}
