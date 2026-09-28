package com.maiya.investigator.mcp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentionParser {
    private static final Pattern MENTION = Pattern.compile("(?:^|[\\s,，、('\"`])@([A-Za-z_./][\\w./$:-]*)");

    private MentionParser() {
    }

    public static List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> mentions = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1).replaceAll("[，。,.;；]+$", "");
            if (!token.isBlank() && token.indexOf('@') < 0) {
                mentions.add(token);
            }
        }
        return new ArrayList<>(mentions);
    }
}
