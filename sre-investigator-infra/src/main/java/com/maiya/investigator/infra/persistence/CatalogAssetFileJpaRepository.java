package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogAssetFileJpaRepository extends JpaRepository<CatalogAssetFileEntity, UUID> {
    List<CatalogAssetFileEntity> findByAssetIdOrderBySortOrderAscPathAsc(UUID assetId);

    void deleteByAssetId(UUID assetId);

    void deleteByAssetIdIn(List<UUID> assetIds);

    long countByAssetId(UUID assetId);
}
