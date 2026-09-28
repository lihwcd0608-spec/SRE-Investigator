package com.maiya.investigator.graph.state;

/** 证据置信度。用于结构化状态与报告排序，不做业务分支硬判定。 */
public enum Confidence {
    LOW,
    MEDIUM,
    HIGH;

    public static Confidence from(String raw) {
        if (raw == null) {
            return MEDIUM;
        }
        return switch (raw.trim().toUpperCase()) {
            case "HIGH", "高" -> HIGH;
            case "LOW", "低" -> LOW;
            default -> MEDIUM;
        };
    }

    public String label() {
        return switch (this) {
            case HIGH -> "高";
            case LOW -> "低";
            default -> "中";
        };
    }
}
