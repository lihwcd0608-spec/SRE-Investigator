package com.maiya.investigator.graph;

public record AgentThought(String kind, String message) {
    public static AgentThought of(String kind, String message) {
        return new AgentThought(kind == null || kind.isBlank() ? "think" : kind, message == null ? "" : message);
    }
}
