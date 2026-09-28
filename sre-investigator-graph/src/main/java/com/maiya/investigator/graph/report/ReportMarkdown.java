package com.maiya.investigator.graph.report;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化报告与四段 Markdown 之间的互转。
 *
 * <p>渲染出的 Markdown 固定包含"已确认的证据"标题，保持与旧前端 {@code <pre>} 渲染
 * 及 {@code looksLikeReport} 检查向后兼容；解析则尽力把已有四段文本还原成结构化字段，
 * 供前端证据面板与结构化 SSE 事件使用。
 */
public final class ReportMarkdown {
    private static final String PHENOMENON = "现象";
    private static final String EVIDENCE = "已确认的证据";
    private static final String RULED_OUT = "已排除项";
    private static final String HYPOTHESES = "假设";
    private static final String NEXT_STEPS = "下一步";

    private ReportMarkdown() {
    }

    public static String render(StructuredReport report) {
        StringBuilder text = new StringBuilder();
        if (report.phenomenon() != null && !report.phenomenon().isBlank()) {
            text.append("## ").append(PHENOMENON).append('\n').append(report.phenomenon()).append("\n\n");
        }
        appendSection(text, EVIDENCE, report.confirmedEvidence());
        appendSection(text, HYPOTHESES, report.hypotheses());
        appendSection(text, RULED_OUT, report.ruledOut());
        appendSection(text, NEXT_STEPS, report.nextSteps());
        return text.toString().strip();
    }

    private static void appendSection(StringBuilder text, String title, List<String> items) {
        text.append("## ").append(title).append('\n');
        if (items == null || items.isEmpty()) {
            text.append("（无）\n\n");
            return;
        }
        for (String item : items) {
            text.append("- ").append(item).append('\n');
        }
        text.append('\n');
    }

    /** 尽力把已有四段 Markdown 解析回结构化字段；无法识别时把全文塞进 confirmedEvidence。 */
    public static StructuredReport parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new StructuredReport("", List.of(), List.of(), List.of(), List.of());
        }
        String phenomenon = "";
        List<String> evidence = new ArrayList<>();
        List<String> ruledOut = new ArrayList<>();
        List<String> hypotheses = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        String current = null;
        StringBuilder phenomenonBuf = new StringBuilder();
        boolean matchedAnySection = false;
        for (String line : markdown.split("\n")) {
            String trimmed = line.strip();
            String heading = heading(trimmed);
            if (heading != null) {
                current = heading;
                matchedAnySection = true;
                continue;
            }
            if (current == null) {
                continue;
            }
            String item = bullet(trimmed);
            switch (current) {
                case PHENOMENON -> {
                    if (!trimmed.isBlank()) {
                        phenomenonBuf.append(phenomenonBuf.isEmpty() ? "" : " ").append(trimmed);
                    }
                }
                case EVIDENCE -> addIfContent(evidence, item, trimmed);
                case RULED_OUT -> addIfContent(ruledOut, item, trimmed);
                case HYPOTHESES -> addIfContent(hypotheses, item, trimmed);
                case NEXT_STEPS -> addIfContent(nextSteps, item, trimmed);
                default -> { }
            }
        }
        phenomenon = phenomenonBuf.toString();
        if (!matchedAnySection) {
            evidence.add(markdown.strip());
        }
        return new StructuredReport(phenomenon, evidence, ruledOut, hypotheses, nextSteps);
    }

    private static void addIfContent(List<String> target, String bullet, String rawLine) {
        String value = bullet != null ? bullet : rawLine;
        if (value != null && !value.isBlank() && !"（无）".equals(value.strip())) {
            target.add(value.strip());
        }
    }

    private static String heading(String line) {
        if (!line.startsWith("#")) {
            return null;
        }
        String text = line.replaceFirst("^#+\\s*", "").strip();
        if (text.contains(PHENOMENON)) {
            return PHENOMENON;
        }
        if (text.contains(EVIDENCE)) {
            return EVIDENCE;
        }
        if (text.contains(RULED_OUT)) {
            return RULED_OUT;
        }
        if (text.contains(HYPOTHESES)) {
            return HYPOTHESES;
        }
        if (text.contains(NEXT_STEPS)) {
            return NEXT_STEPS;
        }
        return null;
    }

    private static String bullet(String line) {
        if (line.startsWith("- ") || line.startsWith("* ")) {
            return line.substring(2).strip();
        }
        if (line.matches("^\\d+[.、)]\\s+.*")) {
            return line.replaceFirst("^\\d+[.、)]\\s+", "").strip();
        }
        return null;
    }
}
