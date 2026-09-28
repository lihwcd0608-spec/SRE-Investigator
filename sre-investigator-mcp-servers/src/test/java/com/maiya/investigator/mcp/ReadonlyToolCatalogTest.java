package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadonlyToolCatalogTest {

    @Test
    void coversAll43LocalTools() {
        List<String> names = ReadonlyToolCatalog.allLocalToolNames();
        assertEquals(43, names.size(), names.toString());
        // 无重复
        assertEquals(names.size(), names.stream().distinct().count());
        // 关键工具在册
        assertTrue(names.contains("mysql_query_readonly"));
        assertTrue(names.contains("sls_query_readonly"));
        assertTrue(names.contains("arms_search_traces"));
        assertTrue(names.contains("lb_health"));
        assertTrue(names.contains("cdn_usage"));
        assertTrue(names.contains("code_read"));
        assertTrue(names.contains("skill_read"));
    }

    @Test
    void mapsKindsToTools() {
        assertEquals(List.of("mysql_query_readonly"), ReadonlyToolCatalog.forKind(ToolKind.MYSQL));
        assertEquals(2, ReadonlyToolCatalog.forKind(ToolKind.REDIS).size());
        assertEquals(9, ReadonlyToolCatalog.forKind(ToolKind.MSE).size());
        assertEquals(3, ReadonlyToolCatalog.forKind(ToolKind.ARMS).size());
        assertEquals(2, ReadonlyToolCatalog.forKind(ToolKind.LOAD_BALANCER).size());
        assertEquals(3, ReadonlyToolCatalog.forKind(ToolKind.CDN).size());
        assertTrue(ReadonlyToolCatalog.forKind(ToolKind.CODE).isEmpty());
    }
}
