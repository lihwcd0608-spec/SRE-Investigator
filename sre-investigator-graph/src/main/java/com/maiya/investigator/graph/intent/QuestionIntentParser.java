package com.maiya.investigator.graph.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Optional;

final class QuestionIntentParser {
    private QuestionIntentParser() {
    }

    static Optional<QuestionIntent> parseModelReply(String reply, ObjectMapper mapper) {
        if (reply == null || reply.isBlank() || mapper == null) {
            return Optional.empty();
        }
        String json = extractObject(reply);
        if (json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(json);
            String action = text(node, "action").toLowerCase(Locale.ROOT);
            if (action.isBlank()) {
                return Optional.empty();
            }
            if (action.equals("go") || action.equals("allow") || action.equals("investigate")
                    || action.equals("review") || action.equals("yes")) {
                return Optional.of(QuestionIntent.go());
            }
            if (action.equals("skip") || action.equals("reject") || action.equals("no")
                    || action.equals("ignore") || action.equals("chat")) {
                return Optional.of(QuestionIntent.skip(text(node, "reply")));
            }
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String extractObject(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return reply.substring(start, end + 1);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }
}
