package com.maiya.investigator.graph.report;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化排查结论。替代"只有一个 report 字符串"的旧做法，便于前端做证据面板、
 * 置信度、可追溯 artifact；同时可序列化回兼容的四段 Markdown。
 *
 * @param phenomenon       现象
 * @param confirmedEvidence 已确认的证据
 * @param ruledOut         已排除项
 * @param hypotheses       根因假设（按可能性排序，最多三个）
 * @param nextSteps        建议下一步
 */
public record StructuredReport(
        String phenomenon,
        List<String> confirmedEvidence,
        List<String> ruledOut,
        List<String> hypotheses,
        List<String> nextSteps
) {
    public StructuredReport {
        phenomenon = phenomenon == null ? "" : phenomenon.trim();
        confirmedEvidence = copy(confirmedEvidence);
        ruledOut = copy(ruledOut);
        hypotheses = copy(hypotheses);
        nextSteps = copy(nextSteps);
    }

    public boolean isEmpty() {
        return phenomenon.isBlank()
                && confirmedEvidence.isEmpty()
                && ruledOut.isEmpty()
                && hypotheses.isEmpty()
                && nextSteps.isEmpty();
    }

    private static List<String> copy(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String item : raw) {
            if (item != null && !item.isBlank()) {
                out.add(item.trim());
            }
        }
        return out;
    }
}
