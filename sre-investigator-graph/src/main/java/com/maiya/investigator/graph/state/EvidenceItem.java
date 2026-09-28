package com.maiya.investigator.graph.state;

/**
 * 一条排查证据。来源必须能对应到工具结果或预读代码，不允许模型凭空编造。
 *
 * @param source  来源标识（工具名，如 mysql_query_readonly / code / model）
 * @param detail  证据正文（已裁剪）
 * @param confidence 置信度
 */
public record EvidenceItem(String source, String detail, Confidence confidence) {
    public EvidenceItem {
        source = source == null ? "" : source.trim();
        detail = detail == null ? "" : detail.trim();
        confidence = confidence == null ? Confidence.MEDIUM : confidence;
    }

    public static EvidenceItem of(String source, String detail) {
        return new EvidenceItem(source, detail, Confidence.MEDIUM);
    }

    /** 去重键：来源 + 正文，避免同一条工具结果被反复登记。 */
    public String dedupeKey() {
        return source + "|" + detail;
    }
}
