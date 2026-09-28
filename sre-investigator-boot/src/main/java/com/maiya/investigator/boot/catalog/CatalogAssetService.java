package com.maiya.investigator.boot.catalog;

import com.maiya.investigator.api.CatalogAssetFileRequest;
import com.maiya.investigator.api.CatalogAssetFileResponse;
import com.maiya.investigator.api.CatalogAssetRequest;
import com.maiya.investigator.api.SkillBundlePreviewResponse;
import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogAssetFile;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogSource;
import com.maiya.investigator.domain.control.CatalogTemplate;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.OpenSourceCatalog;
import com.maiya.investigator.domain.control.RuleUsage;
import com.maiya.investigator.domain.control.ToolAnnotations;
import com.maiya.investigator.infra.audit.AuditTrail;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CatalogAssetService {
    private static final int MAX_CONTENT = 20_000;
    private final ControlPlaneStore controlPlane;
    private final OpenSourceContentFetcher fetcher;
    private final AuditTrail auditTrail;

    public CatalogAssetService(
            ControlPlaneStore controlPlane,
            OpenSourceContentFetcher fetcher,
            AuditTrail auditTrail
    ) {
        this.controlPlane = controlPlane;
        this.fetcher = fetcher;
        this.auditTrail = auditTrail;
    }

    public List<CatalogAssetResponse> list(UUID projectId) {
        List<CatalogAssetResponse> items = new ArrayList<>();
        for (CatalogAsset asset : controlPlane.assets(projectId)) {
            items.add(toResponse(asset));
        }
        return items;
    }

    public CatalogAssetResponse get(UUID projectId, UUID assetId) {
        return toResponse(controlPlane.asset(projectId, assetId));
    }

    public CatalogAssetResponse create(UUID projectId, CatalogAssetRequest request) {
        CatalogKind kind = parseKind(request.kind());
        if (kind == CatalogKind.MCP) {
            throw new IllegalArgumentException("开源 MCP 请在 MCP 连接里保存");
        }
        CatalogTemplate template = OpenSourceCatalog.find(request.catalogId()).orElse(null);
        CatalogAsset draft = apply(CatalogAsset.create(
                projectId,
                kind,
                parseSource(request.source(), template),
                slug(request, kind, template),
                name(request, template),
                Instant.now()
        ), request, template, true);
        ensureUnique(projectId, null, draft.kind(), draft.slug());
        CatalogAsset saved = controlPlane.saveAsset(draft);
        replaceFiles(saved, request.files(), true);
        auditTrail.record("catalog.create", saved.id().toString(), Map.of(
                "projectId", projectId,
                "kind", saved.kind(),
                "source", saved.source(),
                "slug", saved.slug()
        ));
        return toResponse(saved);
    }

    public CatalogAssetResponse update(UUID projectId, UUID assetId, CatalogAssetRequest request) {
        CatalogAsset current = controlPlane.asset(projectId, assetId);
        CatalogTemplate template = OpenSourceCatalog.find(request.catalogId()).orElse(null);
        CatalogAsset next = apply(current, request, template, false);
        ensureUnique(projectId, assetId, next.kind(), next.slug());
        CatalogAsset saved = controlPlane.saveAsset(next);
        replaceFiles(saved, request.files(), false);
        auditTrail.record("catalog.update", saved.id().toString(), Map.of(
                "projectId", projectId,
                "kind", saved.kind(),
                "slug", saved.slug()
        ));
        return toResponse(saved);
    }

    public CatalogAssetResponse setEnabled(UUID projectId, UUID assetId, boolean enabled) {
        CatalogAsset saved = controlPlane.setAssetEnabled(projectId, assetId, enabled);
        auditTrail.record("catalog.enabled", assetId.toString(), Map.of(
                "projectId", projectId,
                "enabled", enabled
        ));
        return toResponse(saved);
    }

    public void delete(UUID projectId, UUID assetId) {
        CatalogAsset current = controlPlane.asset(projectId, assetId);
        controlPlane.deleteAsset(projectId, assetId);
        auditTrail.record("catalog.delete", assetId.toString(), Map.of(
                "projectId", projectId,
                "kind", current.kind(),
                "slug", current.slug()
        ));
    }

    public String preview(String url) {
        return clip(fetcher.fetchMarkdown(url), MAX_CONTENT);
    }

    public SkillBundlePreviewResponse previewBundle(MultipartFile zip, List<MultipartFile> uploads) {
        SkillBundleParser.ParsedBundle parsed = parseUpload(zip, uploads);
        List<CatalogAssetFileResponse> files = new ArrayList<>();
        for (SkillBundleParser.BundleFile file : parsed.files()) {
            files.add(new CatalogAssetFileResponse(file.path(), file.content()));
        }
        return new SkillBundlePreviewResponse(parsed.name(), parsed.summary(), SkillBundleParser.ENTRY_NAME, files);
    }

    public CatalogAssetResponse toResponse(CatalogAsset asset) {
        List<CatalogAssetFileResponse> files = new ArrayList<>();
        for (CatalogAssetFile file : controlPlane.assetFiles(asset.id())) {
            files.add(new CatalogAssetFileResponse(file.path(), file.content()));
        }
        return new CatalogAssetResponse(asset, files);
    }

    private CatalogAsset apply(
            CatalogAsset base,
            CatalogAssetRequest request,
            CatalogTemplate template,
            boolean creating
    ) {
        CatalogSource source = request.source() == null || request.source().isBlank()
                ? (creating ? parseSource(null, template) : base.source())
                : parseSource(request.source(), template);
        String origin = firstNonBlank(request.origin(), template == null ? null : template.origin(), base.origin(), "local");
        String catalogId = firstNonBlank(request.catalogId(), template == null ? null : template.id(), base.catalogId());
        String packageName = firstNonBlank(request.packageName(), template == null ? null : template.packageName(), base.packageName());
        String sourceUrl = firstNonBlank(request.sourceUrl(), template == null ? null : template.sourceUrl(), base.sourceUrl());
        String gitRef = firstNonBlank(request.gitRef(), template == null ? null : template.gitRef(), base.gitRef());
        String command = firstNonBlank(request.command(), template == null ? null : template.command(), base.command());
        String args = firstNonBlank(request.args(), template == null ? null : template.args(), base.args());
        String route = resolveRoute(base.kind(), request.route(), template == null ? null : template.route(), base.route());
        String content = request.content();
        if ((content == null || content.isBlank()) && request.files() != null) {
            content = entryContent(request.files());
        }
        if ((content == null || content.isBlank()) && template != null && template.content() != null) {
            content = template.content();
        }
        if ((content == null || content.isBlank()) && sourceUrl != null && looksFetchable(sourceUrl) && creating) {
            content = preview(sourceUrl);
        }
        if (content == null || content.isBlank()) {
            content = base.content();
        }
        content = clip(content, MAX_CONTENT);
        if (base.kind() != CatalogKind.MCP && (content == null || content.isBlank()) && creating) {
            throw new IllegalArgumentException("请填写正文，或从开源目录/地址导入");
        }
        String summary = ToolAnnotations.clipSummary(firstNonBlank(
                request.summary(),
                creating && template != null ? template.description() : null,
                creating ? null : base.summary()
        ));
        String tags = request.tags() == null
                ? (creating ? null : base.tags())
                : ToolAnnotations.joinTags(request.tags());
        Instant now = Instant.now();
        if (creating) {
            return new CatalogAsset(
                    base.id(),
                    base.projectId(),
                    base.kind(),
                    source,
                    slug(request, base.kind(), template),
                    name(request, template),
                    blankToNull(route),
                    request.enabled() == null || request.enabled(),
                    origin,
                    blankToNull(catalogId),
                    blankToNull(packageName),
                    blankToNull(sourceUrl),
                    blankToNull(gitRef),
                    blankToNull(command),
                    blankToNull(args),
                    content,
                    summary,
                    tags,
                    now,
                    now
            );
        }
        return base.withDetails(
                source,
                slug(request, base.kind(), template),
                name(request, template),
                blankToNull(route),
                origin,
                blankToNull(catalogId),
                blankToNull(packageName),
                blankToNull(sourceUrl),
                blankToNull(gitRef),
                blankToNull(command),
                blankToNull(args),
                content,
                summary,
                tags,
                now
        );
    }

    private void ensureUnique(UUID projectId, UUID excludeId, CatalogKind kind, String slug) {
        boolean taken = controlPlane.assets(projectId).stream().anyMatch(item ->
                item.kind() == kind
                        && item.slug().equals(slug)
                        && (excludeId == null || !item.id().equals(excludeId)));
        if (taken) {
            throw new IllegalArgumentException("同类型下已有相同标识：" + slug);
        }
    }

    private static CatalogKind parseKind(String kind) {
        try {
            return CatalogKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的配置类型：" + kind);
        }
    }

    private static CatalogSource parseSource(String source, CatalogTemplate template) {
        if (source != null && !source.isBlank()) {
            try {
                return CatalogSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("不支持的来源：" + source);
            }
        }
        return template == null ? CatalogSource.CUSTOM : template.source();
    }

    private static String slug(CatalogAssetRequest request, CatalogKind kind, CatalogTemplate template) {
        String fromSlug = asciiSlug(request.slug());
        if (!fromSlug.isBlank()) {
            return clipSlug(fromSlug);
        }
        String fromName = asciiSlug(firstNonBlank(request.name(), template == null ? null : template.id()));
        if (!fromName.isBlank()) {
            return clipSlug(fromName);
        }
        String seed = firstNonBlank(request.slug(), request.name(), template == null ? null : template.id(), kind.name());
        return clipSlug(kind.name().toLowerCase(Locale.ROOT) + "-" + Integer.toHexString(Math.abs(seed.hashCode())));
    }

    private static String asciiSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String clipSlug(String slug) {
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    private static String name(CatalogAssetRequest request, CatalogTemplate template) {
        String name = firstNonBlank(request.name(), template == null ? null : template.name());
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("请填写名称");
        }
        return name.trim();
    }

    private static boolean looksFetchable(String url) {
        return url.startsWith("https://") && (url.contains("raw.githubusercontent.com") || url.contains("/blob/") || url.endsWith(".md"));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String resolveRoute(CatalogKind kind, String requested, String templateRoute, String current) {
        if (kind == CatalogKind.SKILL || kind == CatalogKind.RULE) {
            return RuleUsage.canonicalize(firstNonBlank(requested, templateRoute, current, RuleUsage.INVESTIGATE));
        }
        return firstNonBlank(requested, templateRoute, current);
    }

    private void replaceFiles(CatalogAsset asset, List<CatalogAssetFileRequest> files, boolean creating) {
        if (asset.kind() != CatalogKind.SKILL) {
            return;
        }
        if (files == null) {
            if (creating) {
                controlPlane.replaceAssetFiles(asset.id(), new ArrayList<>());
            }
            return;
        }
        List<SkillBundleParser.UploadedFile> uploaded = new ArrayList<>();
        uploaded.add(new SkillBundleParser.UploadedFile(
                SkillBundleParser.ENTRY_NAME,
                (asset.content() == null ? "" : asset.content()).getBytes(StandardCharsets.UTF_8)
        ));
        List<CatalogAssetFile> stored = new ArrayList<>();
        int order = 0;
        for (CatalogAssetFileRequest file : files) {
            if (file == null || file.path() == null) {
                continue;
            }
            String path = SkillBundleParser.normalizePath(file.path());
            if (path == null || SkillBundleParser.ENTRY_NAME.equalsIgnoreCase(path)) {
                continue;
            }
            String text = file.content() == null ? "" : file.content();
            uploaded.add(new SkillBundleParser.UploadedFile(path, text.getBytes(StandardCharsets.UTF_8)));
            stored.add(CatalogAssetFile.create(asset.id(), path, text, order++));
        }
        SkillBundleParser.parseFiles(uploaded);
        controlPlane.replaceAssetFiles(asset.id(), stored);
    }

    private static SkillBundleParser.ParsedBundle parseUpload(MultipartFile zip, List<MultipartFile> uploads) {
        try {
            if (zip != null && !zip.isEmpty()) {
                return SkillBundleParser.parseZip(zip.getBytes());
            }
            List<SkillBundleParser.UploadedFile> files = new ArrayList<>();
            if (uploads != null) {
                for (MultipartFile upload : uploads) {
                    if (upload == null || upload.isEmpty()) {
                        continue;
                    }
                    files.add(new SkillBundleParser.UploadedFile(upload.getOriginalFilename(), upload.getBytes()));
                }
            }
            return SkillBundleParser.parseFiles(files);
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败：" + exception.getMessage());
        }
    }

    private static String entryContent(List<CatalogAssetFileRequest> files) {
        for (CatalogAssetFileRequest file : files) {
            if (file == null || file.path() == null) {
                continue;
            }
            String path = SkillBundleParser.normalizePath(file.path());
            if (SkillBundleParser.ENTRY_NAME.equalsIgnoreCase(path)) {
                return file.content();
            }
        }
        return null;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
