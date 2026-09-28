package com.maiya.investigator.api;

import java.util.Arrays;
import java.util.Locale;

public enum ConversationMode {
    ASK("提问", "回答问题，可阅读已配置代码仓，不改系统", true),
    INVESTIGATE("排查问题", "只读排查，可使用已绑定的数据和代码仓", true),
    REVIEW("代码审查", "对比两个分支，按已配置 Skill / Rule 只读审查", true),
    CODE("写代码", "根据描述生成或修改代码（即将支持）", false);

    private final String label;
    private final String description;
    private final boolean enabled;

    ConversationMode(String label, String description, boolean enabled) {
        this.label = label;
        this.description = description;
        this.enabled = enabled;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }

    public static ConversationMode from(String value) {
        if (value == null || value.isBlank()) {
            return INVESTIGATE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(item -> item.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的对话模式：" + value));
    }
}
