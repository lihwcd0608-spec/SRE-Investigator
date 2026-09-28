package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogAssetJpaRepository extends JpaRepository<CatalogAssetEntity, UUID> {
    List<CatalogAssetEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
