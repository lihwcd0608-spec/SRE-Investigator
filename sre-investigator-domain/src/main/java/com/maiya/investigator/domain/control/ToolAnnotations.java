package com.maiya.investigator.domain.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ToolAnnotations {
    public static final int SUMMARY_MAX = 280;
    public static final int TAG_MAX_LEN = 16;
    public static final int TAG_MAX_COUNT = 8;

    private ToolAnnotations() {
    }

    public static String clipSummary(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX);
    }

    public static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return normalize(List.of(raw.split("[,，;；\\s]+")));
    }

    public static String joinTags(Collection<String> tags) {
        List<String> clean = normalize(tags);
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    public static List<String> normalize(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String value = tag.trim().replaceAll("\\s+", "");
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > TAG_MAX_LEN) {
                value = value.substring(0, TAG_MAX_LEN);
            }
            unique.add(value);
            if (unique.size() >= TAG_MAX_COUNT) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    public static String catalogLine(String kind, String name, String summary, String tags) {
        StringBuilder line = new StringBuilder("- ").append(kind).append(" / ").append(name);
        String clipped = clipSummary(summary);
        if (clipped != null) {
            line.append("：").append(clipped);
        }
        List<String> parsed = parseTags(tags);
        if (!parsed.isEmpty()) {
            line.append(" [").append(String.join(", ", parsed)).append("]");
        }
        return line.toString();
    }

    public static String kindLabel(ToolKind kind) {
        return switch (kind) {
            case MYSQL -> "MySQL";
            case REDIS -> "Redis";
            case MONGODB -> "MongoDB";
            case ELASTICSEARCH -> "Elasticsearch";
            case LOGS -> "日志";
            case MSE -> "MSE";
            case ARMS -> "ARMS 调用链";
            case LOAD_BALANCER -> "负载均衡";
            case CDN -> "CDN";
            case K8S -> "K8s";
            case STARROCKS -> "StarRocks";
            case KAFKA -> "Kafka";
            case ROCKETMQ -> "RocketMQ";
            case CODE -> "代码目录";
            case MCP_SERVER -> "开源 MCP";
        };
    }
}
