package com.maiya.investigator.graph.conversation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConversationMemoryCompactor {
    private static final Pattern USER_ID = Pattern.compile("(?i)(?:用户\\s*id|user[_\\s-]?id)\\s*[:：=]*\\s*(\\d+)");
    private static final Pattern ORDER_ID = Pattern.compile("(?i)(?:订单\\s*id|order[_\\s-]?id)\\s*[:：=]*\\s*([A-Za-z0-9_-]+)");
    private static final Pattern TRACE_ID = Pattern.compile("(?i)(?:trace[_\\s-]?id)\\s*[:：=]*\\s*([A-Za-z0-9._-]+)");
    private static final Pattern TABLE = Pattern.compile("(?i)(?:表|table)\\s*[`\"']?([A-Za-z][A-Za-z0-9_]{1,63})[`\"']?");
    private static final Pattern API = Pattern.compile("(/(?:api|inner|admin)(?:/[A-Za-z0-9._-]+){1,8})");
    private static final Pattern PREV_IDS = Pattern.compile("标识：(.+)");
    private static final Pattern PREV_LOOKED = Pattern.compile("已查：(.+)");

    private ConversationMemoryCompactor() {
    }

    public static String compact(String previous, String user, String assistant, int maxChars) {
        int limit = maxChars <= 0 ? 600 : maxChars;
        Set<String> ids = new LinkedHashSet<>();
        Set<String> looked = new LinkedHashSet<>();
        parsePrevious(previous, ids, looked);
        collect(ids, USER_ID, "userId=", user, assistant);
        collect(ids, ORDER_ID, "orderId=", user, assistant);
        collect(ids, TRACE_ID, "traceId=", user, assistant);
        collectLooked(looked, TABLE, user, assistant);
        collectLooked(looked, API, user, assistant);
        String conclusion = firstSentence(assistant);
        String pending = pendingOf(assistant);
        StringBuilder text = new StringBuilder();
        if (!ids.isEmpty()) {
            text.append("标识：").append(String.join("，", ids)).append('\n');
        }
        if (!looked.isEmpty()) {
            text.append("已查：").append(String.join("，", looked)).append('\n');
        }
        if (conclusion != null) {
            text.append("结论：").append(conclusion).append('\n');
        }
        if (pending != null) {
            text.append("未决：").append(pending).append('\n');
        }
        return clipHead(text.toString().trim(), limit);
    }

    private static void parsePrevious(String previous, Set<String> ids, Set<String> looked) {
        if (previous == null || previous.isBlank()) {
            return;
        }
        Matcher idLine = PREV_IDS.matcher(previous);
        if (idLine.find()) {
            for (String item : idLine.group(1).split("[，,]")) {
                if (!item.isBlank()) {
                    ids.add(item.trim());
                }
            }
        }
        Matcher lookedLine = PREV_LOOKED.matcher(previous);
        if (lookedLine.find()) {
            for (String item : lookedLine.group(1).split("[，,]")) {
                if (!item.isBlank()) {
                    looked.add(item.trim());
                }
            }
        }
    }

    private static void collect(Set<String> target, Pattern pattern, String prefix, String... texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                target.add(prefix + matcher.group(1));
            }
        }
    }

    private static void collectLooked(Set<String> target, Pattern pattern, String... texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                target.add(matcher.group(1));
            }
        }
    }

    private static String firstSentence(String assistant) {
        if (assistant == null || assistant.isBlank()) {
            return null;
        }
        String compact = assistant.replaceAll("\\s+", " ").trim();
        int cut = indexOfSentence(compact);
        String sentence = cut < 0 ? compact : compact.substring(0, cut);
        if (sentence.length() > 160) {
            sentence = sentence.substring(0, 160);
        }
        return sentence.isBlank() ? null : sentence;
    }

    private static String pendingOf(String assistant) {
        if (assistant == null) {
            return null;
        }
        if (assistant.contains("缺") && (assistant.contains("traceId") || assistant.contains("userId") || assistant.contains("orderId"))) {
            return "仍缺必要标识";
        }
        return null;
    }

    private static int indexOfSentence(String text) {
        int best = -1;
        for (String mark : new String[]{"。", "！", "？", "\n"}) {
            int index = text.indexOf(mark);
            if (index > 8 && (best < 0 || index < best)) {
                best = index + mark.length();
            }
        }
        return best;
    }

    static String clipHead(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(text.length() - maxChars);
    }
}
