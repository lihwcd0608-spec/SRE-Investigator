package com.maiya.investigator.graph.state;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一轮对话排查的结构化累积状态（单次 run 内线程受限使用，非线程安全）。
 *
 * <p>取代"把一切拼进一个巨型 prompt + 结论只有一个字符串"的旧做法：
 * 证据、假设、待验证问题、工具账本都以结构化形式累积，供循环 Graph 的
 * plan/observe/decide 节点程序化推理与去重。
 */
public final class InvestigationState {
    private final String question;
    private final String route;
    private final int budgetMax;

    private final List<EvidenceItem> evidence = new ArrayList<>();
    private final Set<String> evidenceKeys = new LinkedHashSet<>();
    private final List<Hypothesis> hypotheses = new ArrayList<>();
    private final List<String> openQuestions = new ArrayList<>();
    private final List<ToolCallRecord> toolLedger = new ArrayList<>();
    private final Set<String> toolKeys = new LinkedHashSet<>();
    private final List<String> priorFindings = new ArrayList<>();

    private int iterations;
    private int budgetUsed;
    private String phase = "";

    public InvestigationState(String question, String route, int budgetMax) {
        this.question = question == null ? "" : question;
        this.route = route == null ? "" : route;
        this.budgetMax = Math.max(1, budgetMax);
    }

    public String question() {
        return question;
    }

    public String route() {
        return route;
    }

    public int iterations() {
        return iterations;
    }

    public int nextIteration() {
        return ++iterations;
    }

    public String phase() {
        return phase;
    }

    public void phase(String phase) {
        this.phase = phase == null ? "" : phase;
    }

    // --- 轮次结论（循环 Graph 每轮 plan 的产出，用于回喂下一轮） ---

    public void recordRound(int iteration, String content) {
        if (content != null && !content.isBlank()) {
            priorFindings.add("第 " + iteration + " 轮：" + content.trim());
        }
    }

    public List<String> priorFindings() {
        return List.copyOf(priorFindings);
    }

    // --- 证据 ---

    public boolean addEvidence(EvidenceItem item) {
        if (item == null || item.detail().isBlank()) {
            return false;
        }
        if (!evidenceKeys.add(item.dedupeKey())) {
            return false;
        }
        evidence.add(item);
        return true;
    }

    public List<EvidenceItem> evidence() {
        return List.copyOf(evidence);
    }

    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }

    public boolean hasStrongEvidence() {
        return evidence.stream().anyMatch(item -> item.confidence() == Confidence.HIGH);
    }

    // --- 假设 ---

    public void addHypothesis(Hypothesis hypothesis) {
        if (hypothesis != null && !hypothesis.statement().isBlank()) {
            hypotheses.add(hypothesis);
        }
    }

    public List<Hypothesis> hypotheses() {
        return List.copyOf(hypotheses);
    }

    // --- 待验证问题 ---

    public void addOpenQuestion(String question) {
        if (question != null && !question.isBlank() && !openQuestions.contains(question.trim())) {
            openQuestions.add(question.trim());
        }
    }

    public List<String> openQuestions() {
        return List.copyOf(openQuestions);
    }

    // --- 工具账本 ---

    /** 登记一次工具调用；返回 false 表示同参数已经查过（可用于提示模型不要重复）。 */
    public boolean recordToolCall(ToolCallRecord record) {
        if (record == null || record.tool().isBlank()) {
            return false;
        }
        boolean fresh = toolKeys.add(record.dedupeKey());
        toolLedger.add(record);
        if (record.ok()) {
            budgetUsed++;
        }
        return fresh;
    }

    public boolean alreadyCalled(String tool, String argsPreview) {
        return toolKeys.contains((tool == null ? "" : tool.trim()) + "|" + (argsPreview == null ? "" : argsPreview.trim()));
    }

    public List<ToolCallRecord> toolLedger() {
        return List.copyOf(toolLedger);
    }

    // --- 预算 ---

    public int budgetMax() {
        return budgetMax;
    }

    public int budgetUsed() {
        return budgetUsed;
    }

    public void addBudgetUsed(int delta) {
        if (delta > 0) {
            budgetUsed += delta;
        }
    }

    public int remainingBudget() {
        return Math.max(0, budgetMax - budgetUsed);
    }

    public boolean budgetExhausted() {
        return remainingBudget() <= 0;
    }

    /**
     * 把已累积的证据/假设/账本渲染成紧凑上下文，供下一轮 plan 注入模型，
     * 让模型基于"已经查到什么、已经查过什么"决定下一步，而不是从零开始。
     */
    public String toContext() {
        StringBuilder text = new StringBuilder();
        if (!priorFindings.isEmpty()) {
            text.append("此前轮次结论（在此基础上继续，不要从零重来，也不要重复已做过的查询）：\n");
            for (String finding : priorFindings) {
                text.append("- ").append(finding).append('\n');
            }
        }
        if (!evidence.isEmpty()) {
            text.append("已确认证据（不要重复求证）：\n");
            for (EvidenceItem item : evidence) {
                text.append("- [").append(item.source()).append('/').append(item.confidence().label()).append("] ")
                        .append(item.detail()).append('\n');
            }
        }
        if (!hypotheses.isEmpty()) {
            text.append("当前假设：\n");
            for (Hypothesis item : hypotheses) {
                text.append("- ").append(item.statement()).append("（可能性 ")
                        .append(Math.round(item.likelihood() * 100)).append("%）\n");
            }
        }
        if (!toolLedger.isEmpty()) {
            text.append("已调用工具（同参数不要再查）：\n");
            for (ToolCallRecord record : toolLedger) {
                text.append("- ").append(record.tool());
                if (!record.argsPreview().isBlank()) {
                    text.append('(').append(record.argsPreview()).append(')');
                }
                text.append(record.ok() ? " ✓" : " ✗").append('\n');
            }
        }
        if (!openQuestions.isEmpty()) {
            text.append("尚待验证：\n");
            for (String item : openQuestions) {
                text.append("- ").append(item).append('\n');
            }
        }
        text.append("预算：已用 ").append(budgetUsed).append('/').append(budgetMax).append(" 次工具调用。");
        return text.toString();
    }
}
