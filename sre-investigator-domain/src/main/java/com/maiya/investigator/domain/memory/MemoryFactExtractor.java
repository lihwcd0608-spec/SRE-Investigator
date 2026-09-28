package com.maiya.investigator.domain.memory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemoryFactExtractor {
    private static final Pattern TABLE = Pattern.compile("(?i)(?:表|table)\\s*[`\"']?([A-Za-z][A-Za-z0-9_]{1,63})[`\"']?");
    private static final Pattern TYPE = Pattern.compile("\\b([A-Z][A-Za-z0-9]+(?:Service|Mapper|Controller|Processor|Handler|Client))\\b");
    private static final Pattern PITFALL = Pattern.compile("(?:不要|禁止|坑|误把|容易)[^。\\n]{6,80}");

    private MemoryFactExtractor() {
    }

    public record ExtractedFact(String kind, String content) {
    }

    public static List<ExtractedFact> extract(String user, String assistant) {
        Set<String> seen = new LinkedHashSet<>();
        List<ExtractedFact> facts = new ArrayList<>();
        addMatches(facts, seen, "architecture", TABLE, "表 ", user, assistant);
        addMatches(facts, seen, "architecture", TYPE, "类型 ", user, assistant);
        addPitfalls(facts, seen, assistant);
        if (facts.size() > 3) {
            return List.copyOf(facts.subList(0, 3));
        }
        return List.copyOf(facts);
    }

    private static void addMatches(
            List<ExtractedFact> facts,
            Set<String> seen,
            String kind,
            Pattern pattern,
            String prefix,
            String... texts
    ) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(text);
            while (matcher.find() && facts.size() < 3) {
                String value = prefix + matcher.group(1);
                if (seen.add(value)) {
                    facts.add(new ExtractedFact(kind, value));
                }
            }
        }
    }

    private static void addPitfalls(List<ExtractedFact> facts, Set<String> seen, String assistant) {
        if (assistant == null || facts.size() >= 3) {
            return;
        }
        Matcher matcher = PITFALL.matcher(assistant);
        while (matcher.find() && facts.size() < 3) {
            String value = matcher.group().trim();
            if (looksLikeOneOffId(value)) {
                continue;
            }
            if (seen.add(value)) {
                facts.add(new ExtractedFact("pitfall", value));
            }
        }
    }

    static boolean looksLikeOneOffId(String text) {
        return text != null && text.matches("(?s).*\\b(?:userId|orderId|traceId)\\s*[=:：]\\s*\\S+.*");
    }
}
