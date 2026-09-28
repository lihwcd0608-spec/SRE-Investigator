package com.maiya.investigator.graph.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogAssetFile;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogSource;
import com.maiya.investigator.domain.control.RuleUsage;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillLoaderTest {
    @Test
    void prefersEnabledProjectSkillsOverFilesystemAndFiltersScene() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "http-5xx", "HTTP 5xx", "http-5xx", "只读排查 5xx", now));
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "order-payment", "订单支付", "order-payment", "只读排查订单", now));
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "reviewer", "代码审查", "review", "只对照 diff", now));
        store.saveAsset(asset(projectId, CatalogKind.RULE, "readonly", "只读边界", null, "不得写库", now));
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);
        List<String> skills = loader.compactContext(projectId);
        List<String> byScene = loader.compactContext(projectId, "http-5xx");
        List<String> reviewSkills = loader.compactContext(projectId, RuleUsage.REVIEW);
        List<String> rules = loader.compactRules(projectId);
        assertTrue(skills.isEmpty());
        assertEquals(1, byScene.size());
        assertTrue(byScene.get(0).contains("HTTP 5xx"));
        assertEquals(1, reviewSkills.size());
        assertTrue(reviewSkills.get(0).contains("代码审查"));
        assertEquals(1, rules.size());
        assertTrue(rules.get(0).contains("不得写库"));
    }

    @Test
    void blankSceneInvestigateSkillsCapAtTwoSortedByName() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "c", "Charlie", null, "第三条", now));
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "a", "Alpha", null, "第一条", now));
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "b", "Bravo", null, "第二条", now));
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);
        List<String> skills = loader.compactContext(projectId, "http-5xx");
        assertEquals(2, skills.size());
        assertTrue(skills.get(0).contains("Alpha"));
        assertTrue(skills.get(1).contains("Bravo"));
        assertTrue(skills.stream().noneMatch(item -> item.contains("Charlie")));
    }

    @Test
    void reviewDoesNotFallbackWhenOnlyInvestigateSkillsExist() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "http-5xx", "HTTP 5xx", "http-5xx", "只读排查 5xx", now));
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);
        assertTrue(loader.compactContext(projectId, RuleUsage.REVIEW).isEmpty());
        assertTrue(loader.compactContext(projectId, RuleUsage.OTHER).isEmpty());
    }

    @Test
    void compactRulesFilterByUsageAndOtherStaysUnloaded() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        store.saveAsset(asset(projectId, CatalogKind.RULE, "readonly", "只读边界", null, "不得写库", now));
        store.saveAsset(asset(projectId, CatalogKind.RULE, "code-review", "代码审查边界", "code-review", "只对照 diff", now));
        store.saveAsset(asset(projectId, CatalogKind.RULE, "k8s", "K8s", "http-5xx", "不得重启", now));
        store.saveAsset(asset(projectId, CatalogKind.RULE, "style", "文风", "other", "不要用感叹号", now));
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);

        List<String> reviewRules = loader.compactRules(projectId, "code-review");
        assertEquals(1, reviewRules.size());
        assertTrue(reviewRules.get(0).contains("只对照 diff"));

        List<String> investigateRules = loader.compactRules(projectId);
        assertEquals(2, investigateRules.size());
        assertTrue(investigateRules.stream().anyMatch(item -> item.contains("不得写库")));
        assertTrue(investigateRules.stream().anyMatch(item -> item.contains("不得重启")));
        assertTrue(investigateRules.stream().noneMatch(item -> item.contains("只对照 diff")));
        assertTrue(investigateRules.stream().noneMatch(item -> item.contains("不要用感叹号")));

        List<String> otherRules = loader.compactRules(projectId, RuleUsage.OTHER);
        assertEquals(1, otherRules.size());
        assertTrue(otherRules.get(0).contains("不要用感叹号"));

        List<String> pinned = loader.mergePinnedRule(investigateRules, projectId, "style");
        assertEquals(3, pinned.size());
        assertTrue(pinned.stream().anyMatch(item -> item.contains("不要用感叹号")));
    }

    @Test
    void compactSkillListsBundleFiles() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID assetId = UUID.randomUUID();
        store.saveAsset(new CatalogAsset(
                assetId,
                projectId,
                CatalogKind.SKILL,
                CatalogSource.CUSTOM,
                "http-5xx",
                "HTTP 5xx",
                "http-5xx",
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "只读排查 5xx",
                null,
                null,
                now,
                now
        ));
        store.replaceAssetFiles(assetId, List.of(
                CatalogAssetFile.create(assetId, "references/playbook.md", "先看网关", 0)
        ));
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);
        String compact = loader.compactContext(projectId, "http-5xx").get(0);
        assertTrue(compact.contains("附属文件：references/playbook.md"));
        assertTrue(compact.contains("skill_read"));
    }

    @Test
    void compactBySlugPinsOneEnabledSkill() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "code-review", "代码审查", "code-review", "只对照 diff", now));
        store.saveAsset(asset(projectId, CatalogKind.SKILL, "http-5xx", "HTTP 5xx", "http-5xx", "只读排查 5xx", now));
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);
        List<String> pinned = loader.compactBySlug(projectId, "http-5xx");
        assertEquals(1, pinned.size());
        assertTrue(pinned.get(0).contains("HTTP 5xx"));
        assertEquals(1, loader.compactBySlug(projectId, "skill-code-review").size());
        assertTrue(loader.compactBySlug(projectId, "missing").isEmpty());
    }

    @Test
    void reviewAlwaysGetsBuiltinRulesEvenWhenNoneInstalled() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);
        List<String> merged = loader.withBuiltinReviewRules(
                projectId,
                loader.compactRules(projectId, RuleUsage.REVIEW)
        );
        assertTrue(merged.size() >= 5);
        assertTrue(merged.stream().anyMatch(item -> item.contains("代码审查边界")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("数据访问边界")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("Java 工程约束")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("缓存失效契约")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("领域与双端契约")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("demo_xxx.table")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("JetCache")));
    }

    @Test
    void reviewSkipsInstalledBuiltinButKeepsTheRest() {
        InMemoryControlPlaneStore store = new InMemoryControlPlaneStore();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        store.saveAsset(asset(projectId, CatalogKind.RULE, "code-review", "代码审查边界", "review", "只对照 diff", now));
        SkillLoader loader = new SkillLoader("/definitely-missing-skills", store);
        List<String> loaded = loader.compactRules(projectId, RuleUsage.REVIEW);
        List<String> merged = loader.withBuiltinReviewRules(projectId, loaded);
        assertEquals(1, loaded.size());
        assertEquals(1, merged.stream().filter(item -> item.contains("代码审查边界")).count());
        assertTrue(merged.stream().anyMatch(item -> item.contains("只对照 diff")));
        assertTrue(merged.stream().noneMatch(item -> item.contains("只依据 diff")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("数据访问边界")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("Java 工程约束")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("缓存失效契约")));
        assertTrue(merged.stream().anyMatch(item -> item.contains("领域与双端契约")));
    }

    private static CatalogAsset asset(
            UUID projectId,
            CatalogKind kind,
            String slug,
            String name,
            String route,
            String content,
            Instant now
    ) {
        return new CatalogAsset(
                UUID.randomUUID(),
                projectId,
                kind,
                CatalogSource.BUILTIN,
                slug,
                name,
                route,
                true,
                "catalog",
                kind == CatalogKind.SKILL ? "skill-" + slug : "rule-" + slug,
                null,
                null,
                null,
                null,
                null,
                content,
                null,
                null,
                now,
                now
        );
    }
}
