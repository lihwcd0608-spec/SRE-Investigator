package com.maiya.investigator.graph.skill;

import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogAssetFile;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogTemplate;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.OpenSourceCatalog;
import com.maiya.investigator.domain.control.RuleUsage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkillLoader {
    static final int MAX_GENERIC_SKILLS = 2;
    private final Path root;
    private final ControlPlaneStore controlPlane;

    public SkillLoader(
            @Value("${investigator.skills.root:../packages/skills}") String root,
            ControlPlaneStore controlPlane
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.controlPlane = controlPlane;
    }

    public List<String> compactContext(String route) {
        return compactContext(null, route);
    }

    public List<String> compactContext(UUID projectId) {
        return compactContext(projectId, RuleUsage.INVESTIGATE);
    }

    public List<String> compactContext(UUID projectId, String route) {
        String wanted = route == null || route.isBlank() ? RuleUsage.INVESTIGATE : route;
        if (!RuleUsage.INVESTIGATE.equals(RuleUsage.normalize(wanted))) {
            List<String> saved = compactKind(projectId, CatalogKind.SKILL, wanted);
            if (!saved.isEmpty() || hasEnabledKind(projectId, CatalogKind.SKILL)) {
                return saved;
            }
            return saved;
        }
        List<String> selected = compactInvestigateSkills(projectId, wanted);
        if (!selected.isEmpty() || hasEnabledKind(projectId, CatalogKind.SKILL)) {
            return selected;
        }
        return capFallback(filesystemFallback());
    }

    public List<String> compactRules(UUID projectId) {
        return compactRules(projectId, RuleUsage.INVESTIGATE);
    }

    public List<String> compactRules(UUID projectId, String usage) {
        String wanted = usage == null || usage.isBlank() ? RuleUsage.INVESTIGATE : usage;
        return compactKind(projectId, CatalogKind.RULE, wanted);
    }

    private List<String> compactInvestigateSkills(UUID projectId, String wanted) {
        List<String> items = new ArrayList<>();
        if (projectId == null) {
            return items;
        }
        RuleUsage.Parsed need = RuleUsage.parse(wanted);
        List<CatalogAsset> sceneHits = new ArrayList<>();
        List<CatalogAsset> blank = new ArrayList<>();
        for (CatalogAsset item : controlPlane.assets(projectId)) {
            if (!item.enabled() || item.kind() != CatalogKind.SKILL) {
                continue;
            }
            RuleUsage.Parsed asset = RuleUsage.parse(item.route());
            if (!RuleUsage.INVESTIGATE.equals(asset.usage())) {
                continue;
            }
            if (!need.scene().isBlank() && asset.scene().equalsIgnoreCase(need.scene())) {
                sceneHits.add(item);
            } else if (asset.scene().isBlank()) {
                blank.add(item);
            }
        }
        List<CatalogAsset> chosen = !need.scene().isBlank() && !sceneHits.isEmpty() ? sceneHits : blank;
        if (chosen == blank) {
            chosen.sort(Comparator.comparing(
                    asset -> asset.name() == null ? "" : asset.name(),
                    String.CASE_INSENSITIVE_ORDER
            ));
            if (chosen.size() > MAX_GENERIC_SKILLS) {
                chosen = new ArrayList<>(chosen.subList(0, MAX_GENERIC_SKILLS));
            }
        }
        for (CatalogAsset asset : chosen) {
            items.add(compactAsset(asset));
        }
        return items;
    }

    private static List<String> capFallback(List<String> items) {
        List<String> capped = new ArrayList<>();
        if (items == null) {
            return capped;
        }
        int limit = Math.min(MAX_GENERIC_SKILLS, items.size());
        for (int index = 0; index < limit; index++) {
            capped.add(items.get(index));
        }
        return capped;
    }

    private boolean hasEnabledKind(UUID projectId, CatalogKind kind) {
        if (projectId == null) {
            return false;
        }
        for (CatalogAsset item : controlPlane.assets(projectId)) {
            if (item.enabled() && item.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    public List<String> compactBySlug(UUID projectId, String slug) {
        return compactBySlug(projectId, CatalogKind.SKILL, slug);
    }

    public List<String> compactRuleBySlug(UUID projectId, String slug) {
        return compactBySlug(projectId, CatalogKind.RULE, slug);
    }

    public List<String> mergePinnedRule(List<String> loaded, UUID projectId, String slug) {
        List<String> items = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        if (slug == null || slug.isBlank()) {
            return items;
        }
        for (String item : compactRuleBySlug(projectId, slug)) {
            if (!items.contains(item)) {
                items.add(item);
            }
        }
        return items;
    }

    public List<String> withBuiltinReviewRules(UUID projectId, List<String> loaded) {
        List<String> items = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        LinkedHashSet<String> installed = new LinkedHashSet<>();
        if (projectId != null) {
            for (CatalogAsset item : controlPlane.assets(projectId)) {
                if (item.catalogId() != null && !item.catalogId().isBlank()) {
                    installed.add(item.catalogId());
                }
            }
        }
        for (CatalogTemplate template : OpenSourceCatalog.all()) {
            if (template.kind() != CatalogKind.RULE) {
                continue;
            }
            if (!RuleUsage.matches(template.route(), RuleUsage.REVIEW)) {
                continue;
            }
            if (installed.contains(template.id())) {
                continue;
            }
            String text = compactTemplate(template);
            if (!items.contains(text)) {
                items.add(text);
            }
        }
        return items;
    }

    private List<String> compactBySlug(UUID projectId, CatalogKind kind, String slug) {
        List<String> items = new ArrayList<>();
        if (projectId == null || kind == null || slug == null || slug.isBlank()) {
            return items;
        }
        String wanted = slug.trim();
        for (CatalogAsset item : controlPlane.assets(projectId)) {
            if (!item.enabled() || item.kind() != kind) {
                continue;
            }
            if (wanted.equalsIgnoreCase(item.slug()) || wanted.equalsIgnoreCase(item.catalogId())) {
                items.add(compactAsset(item));
                return items;
            }
        }
        return items;
    }

    private List<String> compactKind(UUID projectId, CatalogKind kind, String route) {
        List<String> items = new ArrayList<>();
        if (projectId == null) {
            return items;
        }
        for (CatalogAsset item : controlPlane.assets(projectId)) {
            if (!item.enabled() || item.kind() != kind) {
                continue;
            }
            // Skill / Rule 都按类型（investigate / review / other）和可选场景过滤
            if ((kind == CatalogKind.RULE || kind == CatalogKind.SKILL) && !RuleUsage.matches(item.route(), route)) {
                continue;
            }
            items.add(compactAsset(item));
        }
        return items;
    }

    private String compactAsset(CatalogAsset asset) {
        String body = asset.content() == null ? "" : asset.content().replaceAll("\\s+", " ").trim();
        String excerpt = body.length() > 800 ? body.substring(0, 800) + "…" : body;
        String text = asset.name() + "：" + (excerpt.isBlank() ? "（无正文）" : excerpt);
        if (asset.kind() != CatalogKind.SKILL) {
            return text;
        }
        List<String> extras = new ArrayList<>();
        for (CatalogAssetFile file : controlPlane.assetFiles(asset.id())) {
            if (file.path() != null && !file.path().isBlank() && !"SKILL.md".equalsIgnoreCase(file.path())) {
                extras.add(file.path());
            }
        }
        if (extras.isEmpty()) {
            return text;
        }
        return text + " 附属文件：" + String.join(", ", extras) + "。细节用 skill_read。";
    }

    private String compactTemplate(CatalogTemplate template) {
        String body = template.content() == null ? "" : template.content().replaceAll("\\s+", " ").trim();
        String excerpt = body.length() > 800 ? body.substring(0, 800) + "…" : body;
        return template.name() + "：" + (excerpt.isBlank() ? "（无正文）" : excerpt);
    }

    private List<String> filesystemFallback() {
        if (!Files.isDirectory(root)) {
            return new ArrayList<>();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .map(this::compact)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException exception) {
            List<String> failed = new ArrayList<>();
            failed.add("Skill 加载失败：" + exception.getMessage());
            return failed;
        }
    }

    private String compact(Path skill) {
        try {
            String content = Files.readString(skill, StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim();
            String excerpt = content.length() > 800 ? content.substring(0, 800) + "…" : content;
            return root.relativize(skill) + "：" + excerpt;
        } catch (IOException exception) {
            return root.relativize(skill) + "：读取失败";
        }
    }
}
