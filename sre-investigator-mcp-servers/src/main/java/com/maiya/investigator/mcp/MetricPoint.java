package com.maiya.investigator.mcp;

/** 监控指标单点数据。 */
public record MetricPoint(String key, String label, String value, String unit) {
}
