package com.maiya.investigator.infra.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目记忆的关键词相关度过滤。Qdrant 未启用时，代替「按项目取最近 N 条」的盲目回灌，
 * 只保留与当前问题有词面重合的事实，避免把无关记忆（含别人会话抽出的事实）灌进 prompt。
 */
final class MemoryRelevance {
    private static final Pattern ASCII = Pattern.compile("[a-z0-9_]{2,}");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]+");

    private MemoryRelevance() {
    }

    static List<String> filter(String query, List<String> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return new ArrayList<>();
        }
        List<Scored> scored = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            int overlap = 0;
            for (String token : tokenize(candidate)) {
                if (queryTokens.contains(token)) {
                    overlap++;
                }
            }
            if (overlap > 0) {
                scored.add(new Scored(candidate.trim(), overlap));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        List<String> out = new ArrayList<>();
        for (Scored item : scored) {
            if (out.size() >= limit) {
                break;
            }
            if (!out.contains(item.text())) {
                out.add(item.text());
            }
        }
        return out;
    }

    static Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher ascii = ASCII.matcher(text.toLowerCase(Locale.ROOT));
        while (ascii.find()) {
            tokens.add(ascii.group());
        }
        Matcher cjk = CJK.matcher(text);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() == 1) {
                tokens.add(run);
            } else {
                for (int index = 0; index + 1 < run.length(); index++) {
                    tokens.add(run.substring(index, index + 2));
                }
            }
        }
        return tokens;
    }

    private record Scored(String text, int score) {
    }
}
