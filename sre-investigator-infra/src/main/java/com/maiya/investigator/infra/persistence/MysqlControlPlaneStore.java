package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogAssetFile;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogSource;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MysqlControlPlaneStore implements ControlPlaneStore {
    private final RepositoryJpaRepository repositories;
    private final ToolBindingJpaRepository bindings;
    private final CatalogAssetJpaRepository assets;
    private final CatalogAssetFileJpaRepository assetFiles;

    public MysqlControlPlaneStore(
            RepositoryJpaRepository repositories,
            ToolBindingJpaRepository bindings,
            CatalogAssetJpaRepository assets,
            CatalogAssetFileJpaRepository assetFiles
    ) {
        this.repositories = repositories;
        this.bindings = bindings;
        this.assets = assets;
        this.assetFiles = assetFiles;
    }

    @Override
    public RepositoryConfig saveRepository(RepositoryConfig repository) {
        return toDomain(repositories.save(new RepositoryEntity(
                repository.id(),
                repository.projectId(),
                repository.name(),
                repository.role(),
                repository.rootPath(),
                repository.createdAt(),
                repository.gitUrl(),
                repository.gitRef()
        )));
    }

    @Override
    public List<RepositoryConfig> repositories(UUID projectId) {
        return repositories.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(MysqlControlPlaneStore::toDomain)
                .toList();
    }

    @Override
    public RepositoryConfig repository(UUID projectId, UUID repositoryId) {
        RepositoryEntity entity = repositories.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("代码仓不存在：" + repositoryId));
        if (!projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("代码仓不属于当前项目");
        }
        return toDomain(entity);
    }

    @Override
    public ToolBinding saveBinding(ToolBinding binding, String encryptedConfiguration) {
        return toDomain(bindings.save(new ToolBindingEntity(
                binding.id(),
                binding.projectId(),
                binding.environment().code(),
                binding.toolKind().name(),
                binding.profile(),
                encryptedConfiguration,
                binding.enabled(),
                binding.summary(),
                binding.tags(),
                binding.monitoringEnabled(),
                binding.monitoringMetrics(),
                binding.createdAt(),
                binding.updatedAt()
        )));
    }

    @Override
    public List<ToolBinding> bindings(UUID projectId) {
        return bindings.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(MysqlControlPlaneStore::toDomain)
                .toList();
    }

    @Override
    public List<ToolBinding> bindingsWithMonitoringEnabled() {
        return bindings.findByMonitoringEnabledTrue().stream()
                .map(MysqlControlPlaneStore::toDomain)
                .toList();
    }

    @Override
    public ToolBinding binding(UUID projectId, UUID bindingId) {
        ToolBindingEntity entity = bindings.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在：" + bindingId));
        if (!projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("工具不属于当前项目");
        }
        return toDomain(entity);
    }

    @Override
    public String encryptedBindingConfiguration(UUID bindingId) {
        return bindings.findById(bindingId)
                .map(ToolBindingEntity::getEncryptedConfiguration)
                .orElse(null);
    }

    @Override
    public ToolBinding setBindingEnabled(UUID projectId, UUID bindingId, boolean enabled) {
        ToolBindingEntity entity = bindings.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在：" + bindingId));
        if (!projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("工具不属于当前项目");
        }
        entity.setEnabled(enabled);
        entity.setUpdatedAt(Instant.now());
        return toDomain(bindings.save(entity));
    }

    @Override
    public void deleteRepository(UUID projectId, UUID repositoryId) {
        repository(projectId, repositoryId);
        repositories.deleteById(repositoryId);
    }

    @Override
    public void deleteBinding(UUID projectId, UUID bindingId) {
        binding(projectId, bindingId);
        bindings.deleteById(bindingId);
    }

    @Override
    public CatalogAsset saveAsset(CatalogAsset asset) {
        return toDomain(assets.save(new CatalogAssetEntity(
                asset.id(),
                asset.projectId(),
                asset.kind().name(),
                asset.source().name(),
                asset.slug(),
                asset.name(),
                asset.route(),
                asset.enabled(),
                asset.origin(),
                asset.catalogId(),
                asset.packageName(),
                asset.sourceUrl(),
                asset.gitRef(),
                asset.command(),
                asset.args(),
                asset.content(),
                asset.summary(),
                asset.tags(),
                asset.createdAt(),
                asset.updatedAt()
        )));
    }

    @Override
    public List<CatalogAsset> assets(UUID projectId) {
        return assets.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(MysqlControlPlaneStore::toDomain)
                .toList();
    }

    @Override
    public CatalogAsset asset(UUID projectId, UUID assetId) {
        CatalogAssetEntity entity = assets.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在：" + assetId));
        if (!projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("配置不属于当前项目");
        }
        return toDomain(entity);
    }

    @Override
    public CatalogAsset setAssetEnabled(UUID projectId, UUID assetId, boolean enabled) {
        CatalogAssetEntity entity = assets.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在：" + assetId));
        if (!projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("配置不属于当前项目");
        }
        entity.setEnabled(enabled);
        entity.setUpdatedAt(Instant.now());
        return toDomain(assets.save(entity));
    }

    @Override
    @Transactional
    public void deleteAsset(UUID projectId, UUID assetId) {
        asset(projectId, assetId);
        assetFiles.deleteByAssetId(assetId);
        assets.deleteById(assetId);
    }

    @Override
    public List<CatalogAssetFile> assetFiles(UUID assetId) {
        List<CatalogAssetFile> found = new java.util.ArrayList<>();
        for (CatalogAssetFileEntity entity : assetFiles.findByAssetIdOrderBySortOrderAscPathAsc(assetId)) {
            found.add(toDomain(entity));
        }
        return found;
    }

    @Override
    @Transactional
    public void replaceAssetFiles(UUID assetId, List<CatalogAssetFile> files) {
        assetFiles.deleteByAssetId(assetId);
        if (files == null || files.isEmpty()) {
            return;
        }
        for (CatalogAssetFile file : files) {
            assetFiles.save(new CatalogAssetFileEntity(
                    file.id(),
                    assetId,
                    file.path(),
                    file.content(),
                    file.sortOrder()
            ));
        }
    }

    @Override
    @Transactional
    public void deleteByProjectId(UUID projectId) {
        List<UUID> assetIds = new java.util.ArrayList<>();
        for (CatalogAssetEntity entity : assets.findByProjectIdOrderByUpdatedAtDesc(projectId)) {
            assetIds.add(entity.getId());
        }
        if (!assetIds.isEmpty()) {
            assetFiles.deleteByAssetIdIn(assetIds);
        }
        assets.deleteByProjectId(projectId);
        bindings.deleteByProjectId(projectId);
        repositories.deleteByProjectId(projectId);
    }

    private static RepositoryConfig toDomain(RepositoryEntity entity) {
        return new RepositoryConfig(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getRole(),
                entity.getRootPath(),
                entity.getCreatedAt(),
                entity.getGitUrl(),
                entity.getGitRef()
        );
    }

    private static ToolBinding toDomain(ToolBindingEntity entity) {
        return new ToolBinding(
                entity.getId(),
                entity.getProjectId(),
                Environment.from(entity.getEnvironment()),
                ToolKind.valueOf(entity.getToolType()),
                entity.getProfile(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getEncryptedConfiguration() != null && !entity.getEncryptedConfiguration().isBlank(),
                entity.getSummary(),
                entity.getTags(),
                entity.isMonitoringEnabled(),
                entity.getMonitoringMetrics()
        );
    }

    private static CatalogAsset toDomain(CatalogAssetEntity entity) {
        return new CatalogAsset(
                entity.getId(),
                entity.getProjectId(),
                CatalogKind.valueOf(entity.getKind()),
                CatalogSource.valueOf(entity.getSource()),
                entity.getSlug(),
                entity.getName(),
                entity.getRoute(),
                entity.isEnabled(),
                entity.getOrigin(),
                entity.getCatalogId(),
                entity.getPackageName(),
                entity.getSourceUrl(),
                entity.getGitRef(),
                entity.getCommandLine(),
                entity.getArgs(),
                entity.getContent(),
                entity.getSummary(),
                entity.getTags(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static CatalogAssetFile toDomain(CatalogAssetFileEntity entity) {
        return new CatalogAssetFile(
                entity.getId(),
                entity.getAssetId(),
                entity.getPath(),
                entity.getContent(),
                entity.getSortOrder()
        );
    }
}
