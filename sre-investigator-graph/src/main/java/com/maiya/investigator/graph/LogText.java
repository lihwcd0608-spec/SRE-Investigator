package com.maiya.investigator.graph;

final class LogText {
    private LogText() {
    }

    static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "…" : compact;
    }
}
