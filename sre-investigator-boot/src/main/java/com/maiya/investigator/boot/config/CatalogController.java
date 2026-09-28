package com.maiya.investigator.boot.config;

import com.maiya.investigator.api.CatalogAssetRequest;
import com.maiya.investigator.api.CatalogImportRequest;
import com.maiya.investigator.api.OpenSourceSkillSearchResponse;
import com.maiya.investigator.api.SkillBundlePreviewResponse;
import com.maiya.investigator.api.ToolBindingEnabledRequest;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.boot.catalog.CatalogAssetResponse;
import com.maiya.investigator.boot.catalog.CatalogAssetService;
import com.maiya.investigator.boot.catalog.OpenSourceSkillSearchService;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogTemplate;
import com.maiya.investigator.domain.control.OpenSourceCatalog;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CatalogController {
    private final CatalogAssetService assets;
    private final OpenSourceSkillSearchService skillSearch;
    private final AccessGuard access;

    public CatalogController(CatalogAssetService assets, OpenSourceSkillSearchService skillSearch, AccessGuard access) {
        this.assets = assets;
        this.skillSearch = skillSearch;
        this.access = access;
    }

    @GetMapping("/api/catalog/skills/search")
    @RequirePermission(Permissions.BINDINGS)
    public OpenSourceSkillSearchResponse searchSkills(@RequestParam String q) {
        return skillSearch.search(q);
    }

    @GetMapping("/api/catalog")
    @RequirePermission(any = {Permissions.BINDINGS, Permissions.AGENTS, Permissions.INVESTIGATE, Permissions.REVIEW})
    public List<CatalogTemplate> catalog(@RequestParam(required = false) String kind) {
        if (kind == null || kind.isBlank()) {
            return OpenSourceCatalog.all();
        }
        try {
            return OpenSourceCatalog.byKind(CatalogKind.valueOf(kind.trim().toUpperCase()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的目录类型：" + kind);
        }
    }

    @GetMapping("/api/projects/{projectId}/assets")
    @RequirePermission(any = {Permissions.BINDINGS, Permissions.AGENTS, Permissions.INVESTIGATE, Permissions.REVIEW})
    public List<CatalogAssetResponse> list(@PathVariable UUID projectId) {
        requireProject(projectId);
        return assets.list(projectId);
    }

    @GetMapping("/api/projects/{projectId}/assets/{assetId}")
    @RequirePermission(any = {Permissions.BINDINGS, Permissions.AGENTS, Permissions.INVESTIGATE, Permissions.REVIEW})
    public CatalogAssetResponse get(@PathVariable UUID projectId, @PathVariable UUID assetId) {
        requireProject(projectId);
        return assets.get(projectId, assetId);
    }

    @PostMapping("/api/projects/{projectId}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(any = {Permissions.BINDINGS_WRITE, Permissions.AGENTS_WRITE})
    public CatalogAssetResponse create(@PathVariable UUID projectId, @Valid @RequestBody CatalogAssetRequest request) {
        requireProject(projectId);
        return assets.create(projectId, request);
    }

    @PutMapping("/api/projects/{projectId}/assets/{assetId}")
    @RequirePermission(any = {Permissions.BINDINGS_WRITE, Permissions.AGENTS_WRITE})
    public CatalogAssetResponse update(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @Valid @RequestBody CatalogAssetRequest request
    ) {
        requireProject(projectId);
        return assets.update(projectId, assetId, request);
    }

    @PatchMapping("/api/projects/{projectId}/assets/{assetId}")
    @RequirePermission(any = {Permissions.BINDINGS_WRITE, Permissions.AGENTS_WRITE})
    public CatalogAssetResponse enable(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId,
            @RequestBody ToolBindingEnabledRequest request
    ) {
        requireProject(projectId);
        return assets.setEnabled(projectId, assetId, request.enabled());
    }

    @DeleteMapping("/api/projects/{projectId}/assets/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(any = {Permissions.BINDINGS_DELETE, Permissions.AGENTS_DELETE})
    public void delete(@PathVariable UUID projectId, @PathVariable UUID assetId) {
        requireProject(projectId);
        assets.delete(projectId, assetId);
    }

    @PostMapping("/api/projects/{projectId}/assets/import")
    @RequirePermission(any = {Permissions.BINDINGS_WRITE, Permissions.AGENTS_WRITE})
    public Map<String, String> preview(@PathVariable UUID projectId, @RequestBody CatalogImportRequest request) {
        requireProject(projectId);
        return Map.of("content", assets.preview(request == null ? null : request.url()));
    }

    @PostMapping(value = "/api/projects/{projectId}/assets/import-bundle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(any = {Permissions.BINDINGS_WRITE, Permissions.AGENTS_WRITE})
    public SkillBundlePreviewResponse previewBundle(
            @PathVariable UUID projectId,
            @RequestParam(value = "zip", required = false) MultipartFile zip,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        requireProject(projectId);
        return assets.previewBundle(zip, files);
    }

    private void requireProject(UUID projectId) {
        access.requireProject(projectId);
    }
}
