package com.maiya.investigator.mcp;

import java.util.List;

/** 一次采集得到的指标快照，各数据源（MySQL / Redis / Elasticsearch）通用。 */
public record MetricSnapshot(List<MetricPoint> points) {
}
