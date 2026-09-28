package com.maiya.investigator.graph.intent;

public record QuestionIntent(boolean proceed, String reply) {
    public static QuestionIntent go() {
        return new QuestionIntent(true, "");
    }

    public static QuestionIntent skip(String reply) {
        String text = reply == null || reply.isBlank() ? QuestionIntentHeuristics.DEFAULT_SKIP_REPLY : reply.trim();
        return new QuestionIntent(false, text);
    }
}
