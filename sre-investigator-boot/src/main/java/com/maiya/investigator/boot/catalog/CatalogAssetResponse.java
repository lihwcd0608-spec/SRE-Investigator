package com.maiya.investigator.boot.catalog;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.maiya.investigator.api.CatalogAssetFileResponse;
import com.maiya.investigator.domain.control.CatalogAsset;
import java.util.List;

public record CatalogAssetResponse(
        @JsonUnwrapped CatalogAsset asset,
        List<CatalogAssetFileResponse> files
) {
}
