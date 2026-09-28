package com.maiya.investigator.graph.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportMarkdownTest {

    @Test
    void rendersFourSectionsWithBackwardCompatMarker() {
        StructuredReport report = new StructuredReport(
                "订单接口 5xx",
                List.of("网关日志出现 500", "订单 123 状态异常"),
                List.of("DB 连接正常"),
                List.of("下游超时导致 5xx"),
                List.of("扩大日志时间窗复查")
        );
        String markdown = ReportMarkdown.render(report);
        assertTrue(markdown.contains("## 已确认的证据"), markdown);
        assertTrue(markdown.contains("## 假设"));
        assertTrue(markdown.contains("## 已排除项"));
        assertTrue(markdown.contains("## 下一步"));
        assertTrue(markdown.contains("- 网关日志出现 500"));
        // 兼容旧 looksLikeReport 检查
        assertTrue(markdown.contains("已确认的证据"));
    }

    @Test
    void parseRoundTripsSections() {
        StructuredReport report = new StructuredReport(
                "现象A",
                List.of("证据1"),
                List.of("排除1"),
                List.of("假设1"),
                List.of("下一步1")
        );
        StructuredReport parsed = ReportMarkdown.parse(ReportMarkdown.render(report));
        assertEquals("现象A", parsed.phenomenon());
        assertTrue(parsed.confirmedEvidence().contains("证据1"));
        assertTrue(parsed.ruledOut().contains("排除1"));
        assertTrue(parsed.hypotheses().contains("假设1"));
        assertTrue(parsed.nextSteps().contains("下一步1"));
    }

    @Test
    void parseUnstructuredFallsBackToEvidence() {
        StructuredReport parsed = ReportMarkdown.parse("模型直接输出了一段没有分段的文字");
        assertTrue(parsed.confirmedEvidence().contains("模型直接输出了一段没有分段的文字"));
    }

    @Test
    void emptyReportStillRendersMarker() {
        String markdown = ReportMarkdown.render(new StructuredReport("", List.of(), List.of(), List.of(), List.of()));
        assertTrue(markdown.contains("已确认的证据"));
    }
}
