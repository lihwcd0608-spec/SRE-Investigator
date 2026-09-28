package com.maiya.investigator.graph.conversation;

import java.util.Map;

public record ConversationOutcome(
        String report,
        boolean modelUsed,
        String route,
        Map<String, Object> state
) {
    public boolean budgetExhausted() {
        if (state == null) {
            return false;
        }
        Object value = state.get("budgetExhausted");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    public int usedToolCalls() {
        return intValue("usedToolCalls");
    }

    public int maxToolCalls() {
        return intValue("maxToolCalls");
    }

    private int intValue(String key) {
        if (state == null) {
            return 0;
        }
        Object value = state.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
