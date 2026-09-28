package com.maiya.investigator.graph.conversation;

public final class ContinuationPrompt {
    private ContinuationPrompt() {
    }

    public static String append(String question, String priorQuestion, String priorReport) {
        StringBuilder text = new StringBuilder(question == null ? "" : question);
        text.append("\n\n## 用户确认继续排查\n");
        text.append("上一轮因只读工具次数达到上限而暂停。请在已有证据上继续，不要重复已经做过的 code_search / SQL / Redis 查询。\n");
        if (priorQuestion != null && !priorQuestion.isBlank()) {
            text.append("上一轮问题：\n").append(priorQuestion).append('\n');
        }
        if (priorReport != null && !priorReport.isBlank()) {
            text.append("上一轮结论：\n").append(priorReport).append('\n');
        }
        return text.toString();
    }
}
