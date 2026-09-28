package com.maiya.investigator.domain.control;

import java.util.List;
import java.util.UUID;

public interface ControlPlaneStore {
    RepositoryConfig saveRepository(RepositoryConfig repository);

    List<RepositoryConfig> repositories(UUID projectId);

    RepositoryConfig repository(UUID projectId, UUID repositoryId);

    ToolBinding saveBinding(ToolBinding binding, String encryptedConfiguration);

    List<ToolBinding> bindings(UUID projectId);

    /** 所有已开启监控的 binding（跨项目，供定时采集器使用）。 */
    List<ToolBinding> bindingsWithMonitoringEnabled();

    ToolBinding binding(UUID projectId, UUID bindingId);

    String encryptedBindingConfiguration(UUID bindingId);

    ToolBinding setBindingEnabled(UUID projectId, UUID bindingId, boolean enabled);

    void deleteRepository(UUID projectId, UUID repositoryId);

    void deleteBinding(UUID projectId, UUID bindingId);

    CatalogAsset saveAsset(CatalogAsset asset);

    List<CatalogAsset> assets(UUID projectId);

    CatalogAsset asset(UUID projectId, UUID assetId);

    CatalogAsset setAssetEnabled(UUID projectId, UUID assetId, boolean enabled);

    void deleteAsset(UUID projectId, UUID assetId);

    List<CatalogAssetFile> assetFiles(UUID assetId);

    void replaceAssetFiles(UUID assetId, List<CatalogAssetFile> files);

    void deleteByProjectId(UUID projectId);
}
