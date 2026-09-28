package com.maiya.investigator.domain.control;

import java.util.UUID;

public record CatalogAssetFile(
        UUID id,
        UUID assetId,
        String path,
        String content,
        int sortOrder
) {
    public static CatalogAssetFile create(UUID assetId, String path, String content, int sortOrder) {
        return new CatalogAssetFile(UUID.randomUUID(), assetId, path, content, sortOrder);
    }
}
