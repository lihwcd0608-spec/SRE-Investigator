package com.maiya.investigator.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Environment {
    TEST,
    PRE,
    PROD;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Environment from(String raw) {
        if (raw == null || raw.isBlank()) {
            return TEST;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "test", "local" -> TEST;
            case "pre", "gray", "preview", "staging" -> PRE;
            case "prod", "production", "prd" -> PROD;
            default -> throw new IllegalArgumentException("不支持的环境：" + raw + "，可选 test / pre / prod");
        };
    }
}
