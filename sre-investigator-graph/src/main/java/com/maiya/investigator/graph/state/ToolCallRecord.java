package com.maiya.investigator.graph.state;

/**
 * 工具调用账本条目。用于去重（避免同参数重复查）与向模型回放"已经查过什么"。
 *
 * @param tool        工具名
 * @param argsPreview 入参摘要（已裁剪）
 * @param ok          是否成功
 * @param resultHint  结果摘要（行数/命中数/脱敏值等）
 */
public record ToolCallRecord(String tool, String argsPreview, boolean ok, String resultHint) {
    public ToolCallRecord {
        tool = tool == null ? "" : tool.trim();
        argsPreview = argsPreview == null ? "" : argsPreview.trim();
        resultHint = resultHint == null ? "" : resultHint.trim();
    }

    /** 去重键：工具名 + 入参摘要。 */
    public String dedupeKey() {
        return tool + "|" + argsPreview;
    }
}
