package com.maiya.investigator.domain.control;

import java.util.Locale;

/**
 * Skill / Rule 共用 {@link CatalogAsset#route()}：类型（对话排查 / 代码审查 / 其他）+ 可选场景。
 * 对话只自动装 investigate；审查只自动装 review；other 仅在对话里点名时装载。
 * 有场景时，同类型且场景为空的条目仍会装载。
 */
public final class RuleUsage {
    public static final String INVESTIGATE = "investigate";
    public static final String REVIEW = "review";
    public static final String OTHER = "other";

    public record Parsed(String usage, String scene) {
    }

    private RuleUsage() {
    }

    public static Parsed parse(String route) {
        if (route == null || route.isBlank()) {
            return new Parsed(INVESTIGATE, "");
        }
        String value = route.trim();
        int colon = value.indexOf(':');
        String head = (colon >= 0 ? value.substring(0, colon) : value).trim().toLowerCase(Locale.ROOT);
        String tail = colon >= 0 ? value.substring(colon + 1).trim() : "";
        if (REVIEW.equals(head) || "code-review".equals(head)) {
            return new Parsed(REVIEW, tail);
        }
        if (OTHER.equals(head)) {
            return new Parsed(OTHER, tail);
        }
        if (INVESTIGATE.equals(head) || "always".equals(head) || "conversation".equals(head)) {
            return new Parsed(INVESTIGATE, tail);
        }
        return new Parsed(INVESTIGATE, value);
    }

    public static String normalize(String route) {
        return parse(route).usage();
    }

    public static String sceneOf(String route) {
        return parse(route).scene();
    }

    public static String compose(String usage, String scene) {
        String type = parse(usage).usage();
        String extra = scene == null ? "" : scene.trim();
        if (extra.isBlank()
                || extra.equalsIgnoreCase(type)
                || extra.equalsIgnoreCase("code-review")) {
            return type;
        }
        if (INVESTIGATE.equals(type)) {
            return extra;
        }
        return type + ":" + extra;
    }

    public static String canonicalize(String route) {
        Parsed parsed = parse(route);
        return compose(parsed.usage(), parsed.scene());
    }

    public static boolean matches(String assetRoute, String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return false;
        }
        Parsed asset = parse(assetRoute);
        Parsed need = parse(wanted);
        if (!asset.usage().equals(need.usage())) {
            return false;
        }
        if (need.scene().isBlank()) {
            return true;
        }
        return asset.scene().isBlank() || asset.scene().equalsIgnoreCase(need.scene());
    }
}
