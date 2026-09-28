package com.maiya.investigator.api;

import java.util.ArrayList;
import java.util.List;

public record McpConnectionTestResponse(boolean ok, String message, List<String> clusters) {
    public McpConnectionTestResponse {
        clusters = clusters == null ? new ArrayList<>() : new ArrayList<>(clusters);
    }

    public static McpConnectionTestResponse success(String message) {
        return success(message, new ArrayList<>());
    }

    public static McpConnectionTestResponse success(String message, List<String> clusters) {
        return new McpConnectionTestResponse(true, message, clusters);
    }

    public static McpConnectionTestResponse failure(String message) {
        return failure(message, new ArrayList<>());
    }

    public static McpConnectionTestResponse failure(String message, List<String> clusters) {
        return new McpConnectionTestResponse(false, message, clusters);
    }
}
