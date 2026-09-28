package com.maiya.investigator.graph.route;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InvestigationRouteMatcher {
    private static final Pattern FENCE = Pattern.compile("```(?:[a-zA-Z0-9_-]+)?\\s*([\\s\\S]*?)```");

    private InvestigationRouteMatcher() {
    }

    public static Optional<InvestigationRoute> parseModelReply(String raw, List<InvestigationRoute> routes) {
        if (raw == null || raw.isBlank() || routes == null || routes.isEmpty()) {
            return Optional.empty();
        }
        String text = unwrapFence(raw).trim();
        if (text.isBlank()) {
            return Optional.empty();
        }
        String firstLine = text.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse("");
        String token = firstLine.replaceAll("^[\"'`\\s]+|[\"'`\\s.。，,;；:：]+$", "");
        Optional<InvestigationRoute> exact = findById(token, routes);
        if (exact.isPresent()) {
            return exact;
        }
        int colon = token.lastIndexOf(':') >= 0 ? token.lastIndexOf(':') : token.lastIndexOf('：');
        if (colon >= 0 && colon < token.length() - 1) {
            Optional<InvestigationRoute> afterColon = findById(token.substring(colon + 1).trim(), routes);
            if (afterColon.isPresent()) {
                return afterColon;
            }
        }
        Optional<InvestigationRoute> embedded = findEmbeddedId(text, routes);
        if (embedded.isPresent()) {
            return embedded;
        }
        return findByLabel(text, routes);
    }

    public static Optional<InvestigationRoute> matchKeywords(String question, List<InvestigationRoute> routes) {
        if (question == null || question.isBlank() || routes == null || routes.isEmpty()) {
            return Optional.empty();
        }
        String haystack = question.toLowerCase(Locale.ROOT);
        InvestigationRoute best = null;
        int bestHits = 0;
        int bestLength = 0;
        for (InvestigationRoute route : routes) {
            int hits = 0;
            int length = 0;
            for (String keyword : route.keywords()) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                    hits += 1;
                    length += keyword.length();
                }
            }
            if (hits == 0) {
                continue;
            }
            if (best == null
                    || hits > bestHits
                    || (hits == bestHits && length > bestLength)
                    || (hits == bestHits && length == bestLength && best.fallback() && !route.fallback())) {
                best = route;
                bestHits = hits;
                bestLength = length;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<InvestigationRoute> findById(String id, List<InvestigationRoute> routes) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return routes.stream().filter(item -> item.id().equals(normalized)).findFirst();
    }

    private static Optional<InvestigationRoute> findEmbeddedId(String text, List<InvestigationRoute> routes) {
        String haystack = text.toLowerCase(Locale.ROOT);
        return routes.stream()
                .sorted(Comparator.comparingInt((InvestigationRoute item) -> item.id().length()).reversed())
                .filter(item -> containsToken(haystack, item.id()))
                .findFirst();
    }

    private static Optional<InvestigationRoute> findByLabel(String text, List<InvestigationRoute> routes) {
        String haystack = text.toLowerCase(Locale.ROOT);
        return routes.stream()
                .filter(item -> item.label() != null && !item.label().isBlank())
                .filter(item -> haystack.contains(item.label().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private static boolean containsToken(String haystack, String id) {
        Matcher matcher = Pattern.compile("(?<![a-z0-9-])" + Pattern.quote(id) + "(?![a-z0-9-])").matcher(haystack);
        return matcher.find();
    }

    private static String unwrapFence(String raw) {
        Matcher matcher = FENCE.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return raw;
    }
}
