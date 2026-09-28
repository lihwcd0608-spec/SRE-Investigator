package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.CatalogAsset;
import com.maiya.investigator.domain.control.CatalogKind;
import com.maiya.investigator.domain.control.CatalogSource;
import com.maiya.investigator.domain.control.ToolAnnotations;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolCatalog;
import com.maiya.investigator.domain.control.ToolKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolCatalogTest {
    @Test
    void clipsAndDeduplicatesTags() {
        assertEquals("订单库 只读查询", ToolAnnotations.clipSummary("  订单库   只读查询  "));
        assertEquals("订单,mysql,abcdefghijklmnop", ToolAnnotations.joinTags(List.of(" 订单 ", "mysql", "订单", "abcdefghijklmnopq")));
        assertEquals(List.of("订单", "支付"), ToolAnnotations.parseTags("订单，支付 订单"));
    }

    @Test
    void buildsCatalogLinesForEnabledTools() {
        Instant now = Instant.now();
        UUID projectId = UUID.randomUUID();
        ToolBinding mysql = new ToolBinding(
                UUID.randomUUID(),
                projectId,
                Environment.TEST,
                ToolKind.MYSQL,
                "test",
                true,
                now,
                now,
                true,
                "订单库只读",
                "订单,mysql"
        );
        ToolBinding disabled = mysql.withEnabled(false, now);
        CatalogAsset skill = new CatalogAsset(
                UUID.randomUUID(),
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
                "网关超时与下游 5xx",
                "5xx,超时",
                now,
                now
        );
        CatalogAsset agent = new CatalogAsset(
                UUID.randomUUID(),
                projectId,
                CatalogKind.AGENT,
                CatalogSource.BUILTIN,
                "sre-investigate",
                "SRE 排障",
                "sre-investigate",
                true,
                "official",
                "agent-sre-investigate",
                null,
                null,
                null,
                null,
                null,
                "{\"systemPrompt\":\"x\"}",
                "默认排障",
                "official",
                now,
                now
        );
        List<String> lines = ToolCatalog.lines(List.of(mysql, disabled), List.of(skill, agent));
        assertEquals(2, lines.size());
        assertEquals("- MCP MySQL / test：订单库只读 [订单, mysql]", lines.get(0));
        assertEquals("- SKILL / HTTP 5xx：网关超时与下游 5xx [5xx, 超时]", lines.get(1));
        assertTrue(ToolAnnotations.catalogLine("RULE", "只读边界", null, null).startsWith("- RULE / 只读边界"));
    }
}
