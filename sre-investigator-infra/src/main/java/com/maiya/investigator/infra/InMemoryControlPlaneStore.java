package com.maiya.investigator.infra;

import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogAssetFile;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.domain.control.ToolBinding;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryControlPlaneStore implements ControlPlaneStore {
    private final ConcurrentHashMap<UUID, RepositoryConfig> repositories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ToolBinding> bindings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> encryptedConfigurations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CatalogAsset> assets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<CatalogAssetFile>> assetFiles = new ConcurrentHashMap<>();

    @Override
    public RepositoryConfig saveRepository(RepositoryConfig repository) {
        repositories.put(repository.id(), repository);
        return repository;
    }

    @Override
    public List<RepositoryConfig> repositories(UUID projectId) {
        return repositories.values().stream()
                .filter(repository -> repository.projectId().equals(projectId))
                .sorted(java.util.Comparator.comparing(RepositoryConfig::createdAt))
                .toList();
    }

    @Override
    public RepositoryConfig repository(UUID projectId, UUID repositoryId) {
        RepositoryConfig repository = repositories.get(repositoryId);
        if (repository == null || !repository.projectId().equals(projectId)) {
            throw new IllegalArgumentException("代码仓不存在：" + repositoryId);
        }
        return repository;
    }

    @Override
    public ToolBinding saveBinding(ToolBinding binding, String encryptedConfiguration) {
        ToolBinding stored = binding.withConfigured(encryptedConfiguration != null && !encryptedConfiguration.isBlank());
        bindings.put(stored.id(), stored);
        if (encryptedConfiguration != null) {
            encryptedConfigurations.put(stored.id(), encryptedConfiguration);
        }
        return stored;
    }

    @Override
    public List<ToolBinding> bindings(UUID projectId) {
        return bindings.values().stream()
                .filter(binding -> binding.projectId().equals(projectId))
                .sorted(java.util.Comparator.comparing(ToolBinding::updatedAt).reversed())
                .toList();
    }

    @Override
    public List<ToolBinding> bindingsWithMonitoringEnabled() {
        return bindings.values().stream()
                .filter(ToolBinding::monitoringEnabled)
                .toList();
    }

    @Override
    public ToolBinding binding(UUID projectId, UUID bindingId) {
        ToolBinding binding = bindings.get(bindingId);
        if (binding == null || !binding.projectId().equals(projectId)) {
            throw new IllegalArgumentException("工具不存在：" + bindingId);
        }
        return binding;
    }

    @Override
    public String encryptedBindingConfiguration(UUID bindingId) {
        return encryptedConfigurations.get(bindingId);
    }

    @Override
    public ToolBinding setBindingEnabled(UUID projectId, UUID bindingId, boolean enabled) {
        ToolBinding current = binding(projectId, bindingId);
        ToolBinding updated = current.withEnabled(enabled, java.time.Instant.now());
        bindings.put(updated.id(), updated);
        return updated;
    }

    @Override
    public void deleteRepository(UUID projectId, UUID repositoryId) {
        repository(projectId, repositoryId);
        repositories.remove(repositoryId);
    }

    @Override
    public void deleteBinding(UUID projectId, UUID bindingId) {
        binding(projectId, bindingId);
        bindings.remove(bindingId);
        encryptedConfigurations.remove(bindingId);
    }

    @Override
    public CatalogAsset saveAsset(CatalogAsset asset) {
        assets.put(asset.id(), asset);
        return asset;
    }

    @Override
    public List<CatalogAsset> assets(UUID projectId) {
        return assets.values().stream()
                .filter(item -> item.projectId().equals(projectId))
                .sorted(java.util.Comparator.comparing(CatalogAsset::updatedAt).reversed())
                .toList();
    }

    @Override
    public CatalogAsset asset(UUID projectId, UUID assetId) {
        CatalogAsset item = assets.get(assetId);
        if (item == null || !item.projectId().equals(projectId)) {
            throw new IllegalArgumentException("配置不存在：" + assetId);
        }
        return item;
    }

    @Override
    public CatalogAsset setAssetEnabled(UUID projectId, UUID assetId, boolean enabled) {
        CatalogAsset current = asset(projectId, assetId);
        CatalogAsset updated = current.withEnabled(enabled, java.time.Instant.now());
        assets.put(updated.id(), updated);
        return updated;
    }

    @Override
    public void deleteAsset(UUID projectId, UUID assetId) {
        asset(projectId, assetId);
        assets.remove(assetId);
        assetFiles.remove(assetId);
    }

    @Override
    public List<CatalogAssetFile> assetFiles(UUID assetId) {
        List<CatalogAssetFile> found = new java.util.ArrayList<>();
        List<CatalogAssetFile> stored = assetFiles.get(assetId);
        if (stored != null) {
            found.addAll(stored);
        }
        return found;
    }

    @Override
    public void replaceAssetFiles(UUID assetId, List<CatalogAssetFile> files) {
        List<CatalogAssetFile> stored = new java.util.ArrayList<>();
        if (files != null) {
            stored.addAll(files);
        }
        stored.sort(java.util.Comparator.comparing(CatalogAssetFile::sortOrder).thenComparing(CatalogAssetFile::path));
        assetFiles.put(assetId, stored);
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        repositories.values().removeIf(repository -> repository.projectId().equals(projectId));
        bindings.values().removeIf(binding -> {
            if (!binding.projectId().equals(projectId)) {
                return false;
            }
            encryptedConfigurations.remove(binding.id());
            return true;
        });
        assets.values().removeIf(item -> {
            if (!item.projectId().equals(projectId)) {
                return false;
            }
            assetFiles.remove(item.id());
            return true;
        });
    }
}
