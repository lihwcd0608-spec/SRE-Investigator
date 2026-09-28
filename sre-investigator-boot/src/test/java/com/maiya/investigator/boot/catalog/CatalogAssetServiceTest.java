package com.maiya.investigator.boot.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.CatalogAssetRequest;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogSource;
import com.maiya.investigator.domain.control.OpenSourceCatalog;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import com.maiya.investigator.infra.audit.AuditTrail;
import com.maiya.investigator.infra.persistence.AuditEventJpaRepository;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CatalogAssetServiceTest {
    @Test
    void createsBuiltinSkillFromCatalog() {
        CatalogAssetService service = service();
        UUID projectId = UUID.randomUUID();
        var saved = service.create(projectId, new CatalogAssetRequest(
                "SKILL",
                "BUILTIN",
                null,
                "HTTP 5xx 排查",
                null,
                true,
                null,
                "skill-http-5xx",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        assertEquals(CatalogKind.SKILL, saved.asset().kind());
        assertEquals(CatalogSource.BUILTIN, saved.asset().source());
        assertEquals("http-5xx", saved.asset().route());
        assertTrue(saved.asset().content().contains("HTTP 5xx"));
        assertEquals("内置：网关错误、超时和下游 5xx。", saved.asset().summary());
        assertTrue(OpenSourceCatalog.all().size() >= 8);
        assertTrue(OpenSourceCatalog.find("rule-review-mysql-mapper").isPresent());
        assertTrue(OpenSourceCatalog.find("rule-review-java-style").isPresent());
        assertTrue(OpenSourceCatalog.find("rule-review-jetcache").isPresent());
        assertTrue(OpenSourceCatalog.find("rule-review-domain-boundary").isPresent());
    }

    @Test
    void persistsRuleUsageAndNormalizesLegacyReviewRoute() {
        CatalogAssetService service = service();
        UUID projectId = UUID.randomUUID();
        var review = service.create(projectId, new CatalogAssetRequest(
                "RULE",
                "BUILTIN",
                null,
                "代码审查边界",
                "code-review",
                true,
                null,
                "rule-code-review",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        assertEquals("review", review.asset().route());
        var other = service.create(projectId, new CatalogAssetRequest(
                "RULE",
                "CUSTOM",
                "style-guide",
                "文风",
                "other",
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "不要用感叹号",
                null,
                null,
                null
        ));
        assertEquals("other", other.asset().route());
        var investigate = service.create(projectId, new CatalogAssetRequest(
                "RULE",
                "CUSTOM",
                "readonly-bound",
                "只读边界",
                null,
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "不得写库",
                null,
                null,
                null
        ));
        assertEquals("investigate", investigate.asset().route());
        var skillReview = service.create(projectId, new CatalogAssetRequest(
                "SKILL",
                "CUSTOM",
                "review-playbook",
                "审查提纲",
                "review",
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "只对照 diff",
                null,
                null,
                null
        ));
        assertEquals("review", skillReview.asset().route());
        var skillOther = service.create(projectId, new CatalogAssetRequest(
                "SKILL",
                "CUSTOM",
                "style-skill",
                "文风提纲",
                "other:文风",
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "不要用感叹号",
                null,
                null,
                null
        ));
        assertEquals("other:文风", skillOther.asset().route());
    }

    @Test
    void createsBuiltinAgentFromCatalog() {
        CatalogAssetService service = service();
        var saved = service.create(UUID.randomUUID(), new CatalogAssetRequest(
                "AGENT",
                "BUILTIN",
                "sre-investigate",
                "SRE 排障",
                null,
                true,
                null,
                "agent-sre-investigate",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        assertEquals(CatalogKind.AGENT, saved.asset().kind());
        assertEquals("sre-investigate", saved.asset().slug());
        assertEquals(CatalogSource.BUILTIN, saved.asset().source());
        assertTrue(saved.asset().content().contains("allowedToolKinds"));
        assertTrue(saved.asset().content().contains("systemPrompt"));
    }

    @Test
    void rejectsMcpAssets() {
        CatalogAssetService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.create(UUID.randomUUID(), new CatalogAssetRequest(
                "MCP", "OPEN_SOURCE", "github", "GitHub", null, true, "npm", "mcp-github",
                null, null, null, null, null, null, null, null, null
        )));
    }

    @Test
    void rejectsDuplicateSlug() {
        CatalogAssetService service = service();
        UUID projectId = UUID.randomUUID();
        CatalogAssetRequest request = new CatalogAssetRequest(
                "RULE", "CUSTOM", "readonly", "只读边界", null, true, "local",
                null, null, null, null, null, null, "不得写库", null, null, null
        );
        service.create(projectId, request);
        assertThrows(IllegalArgumentException.class, () -> service.create(projectId, request));
    }

    @Test
    void persistsSummaryAndTags() {
        CatalogAssetService service = service();
        UUID projectId = UUID.randomUUID();
        var saved = service.create(projectId, new CatalogAssetRequest(
                "RULE",
                "CUSTOM",
                "readonly-bound",
                "只读边界",
                null,
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "不得写库",
                "排查时禁止写库或删缓存",
                List.of("只读", "边界", "只读"),
                null
        ));
        assertEquals("排查时禁止写库或删缓存", saved.asset().summary());
        assertEquals("只读,边界", saved.asset().tags());
    }

    @Test
    void acceptsChineseNameAndSlug() {
        CatalogAssetService service = service();
        var saved = service.create(UUID.randomUUID(), new CatalogAssetRequest(
                "RULE",
                "CUSTOM",
                "规则",
                "跨库与用户分表",
                null,
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "同一 MCP 可跨库查询",
                "跨库与用户分表规则",
                List.of("全局规则"),
                null
        ));
        assertEquals("跨库与用户分表", saved.asset().name());
        assertTrue(saved.asset().slug().startsWith("rule-"));
        assertTrue(saved.asset().slug().matches("rule-[0-9a-f]+"));
    }

    @Test
    void persistsSkillBundleFiles() {
        CatalogAssetService service = service();
        UUID projectId = UUID.randomUUID();
        var saved = service.create(projectId, new CatalogAssetRequest(
                "SKILL",
                "CUSTOM",
                "http-5xx-bundle",
                "多文件巡检",
                "http-5xx",
                true,
                "local",
                null,
                null,
                null,
                null,
                null,
                null,
                "# 入口",
                "多文件 Skill",
                List.of("5xx"),
                List.of(
                        new com.maiya.investigator.api.CatalogAssetFileRequest("SKILL.md", "# 入口"),
                        new com.maiya.investigator.api.CatalogAssetFileRequest("references/playbook.md", "先看网关")
                )
        ));
        assertEquals(1, saved.files().size());
        assertEquals("references/playbook.md", saved.files().get(0).path());
        assertEquals("先看网关", saved.files().get(0).content());
        assertEquals("# 入口", saved.asset().content());
        assertEquals("http-5xx", saved.asset().route());
    }

    private static CatalogAssetService service() {
        return new CatalogAssetService(
                new InMemoryControlPlaneStore(),
                new OpenSourceContentFetcher(),
                new AuditTrail(new ObjectProvider<>() {
                    @Override
                    public AuditEventJpaRepository getObject() {
                        return null;
                    }

                    @Override
                    public AuditEventJpaRepository getObject(Object... args) {
                        return null;
                    }

                    @Override
                    public AuditEventJpaRepository getIfAvailable() {
                        return null;
                    }

                    @Override
                    public AuditEventJpaRepository getIfUnique() {
                        return null;
                    }

                    @Override
                    public Iterator<AuditEventJpaRepository> iterator() {
                        return List.<AuditEventJpaRepository>of().iterator();
                    }
                })
        );
    }
}
