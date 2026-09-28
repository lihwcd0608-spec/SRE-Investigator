package com.maiya.investigator.boot.feishu;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FeishuMessageParser {
    private static final Pattern REVIEW_RANGE = Pattern.compile(
            "(?i)(?:审查|review|#review)\\s+(?<base>[^.\\s]+)\\.\\.(?<head>\\S+)\\s*(?<question>.*)$"
    );
    private static final Pattern REVIEW_EXPLICIT = Pattern.compile(
            "(?i)(?:审查|review|#review)\\s+base:(?<base>\\S+)\\s+head:(?<head>\\S+)\\s*(?<question>.*)$"
    );
    /** 句中任意位置：审查 / review A..B */
    private static final Pattern REVIEW_RANGE_INLINE = Pattern.compile(
            "(?i)(?:审查|review|#review)\\s+(?<base>[^.\\s]+)\\.\\.(?<head>\\S+)"
    );
    private static final Pattern REVIEW_EXPLICIT_INLINE = Pattern.compile(
            "(?i)(?:审查|review|#review)\\s+base:(?<base>\\S+)\\s+head:(?<head>\\S+)"
    );
    /** feature/xxx分支和release分支 */
    private static final Pattern TWO_BRANCH_CN = Pattern.compile(
            "(?i)(?<a>[\\w./_-]+)\\s*分支\\s*(?:和|与|跟|、|,|，)\\s*(?<b>[\\w./_-]+)\\s*分支"
    );
    /** 拉取/对比 A 和 B（分支） */
    private static final Pattern PULL_COMPARE = Pattern.compile(
            "(?i)(?:拉取|对比|比较|diff)\\s+(?<a>[\\w./_-]+)\\s*(?:分支)?\\s*(?:和|与|跟|、|,|，)\\s*(?<b>[\\w./_-]+)\\s*(?:分支)?"
    );
    private static final Pattern VS_RANGE = Pattern.compile(
            "(?i)(?<a>[\\w./_-]+)\\s+(?:vs\\.?|versus)\\s+(?<b>[\\w./_-]+)"
    );
    private static final Pattern DOT_RANGE = Pattern.compile(
            "(?<a>[\\w./_-]+)\\s*\\.\\.\\s*(?<b>[\\w./_-]+)"
    );
    private static final Pattern REVIEW_PHRASE = Pattern.compile(
            "(?i)(?:代码\\s*review|代码审查|code\\s*review|#review)"
    );
    private static final Pattern REVIEW_VERB = Pattern.compile("(?i)(?:审查|review)");
    private static final Pattern BRANCH_SIGNAL = Pattern.compile(
            "(?i)(?:分支|feature/|hotfix/|bugfix/|release/|base:|head:|\\.\\.|\\svs\\.?\\s)"
    );
    /** 环境:prod / env:test。必须带冒号，避免把普通问句里的「环境」吃掉。 */
    private static final Pattern LABELED_ENV = Pattern.compile(
            "(?is)^(?:环境|env)\\s*[:：]\\s*(?<env>[\\w\\u4e00-\\u9fff-]+)\\s+(?<rest>.*)$"
    );
    /**
     * prod环境 / 测试环境。环境码必须紧贴「环境」，
     * 避免把「test 环境 user 123 订单没到账」里的 test 当成环境。
     */
    private static final Pattern COMPACT_ENV = Pattern.compile(
            "(?is)^(?<env>production|staging|preview|local|gray|test|pre|prod|prd|测试|预发|灰度|生产)环境(?:\\s*[:：]\\s*|\\s+)(?<rest>.*)$"
    );
    private static final Set<String> STABLE_REFS = Set.of(
            "main", "master", "release", "develop", "dev", "trunk", "prod", "production"
    );

    private FeishuMessageParser() {
    }

    public record ParsedMessage(
            ConversationMode mode,
            String question,
            String baseRef,
            String headRef,
            Environment environment,
            boolean resetThread
    ) {
    }

    public static ParsedMessage parse(String raw) {
        EnvPrefix prefix = stripEnvironmentPrefix(FeishuText.stripMentions(raw));
        String text = prefix.text();
        Environment environment = prefix.environment();
        if (text.isBlank()) {
            return new ParsedMessage(ConversationMode.INVESTIGATE, "", null, null, environment, false);
        }
        if (isReset(text)) {
            return new ParsedMessage(ConversationMode.INVESTIGATE, "", null, null, environment, true);
        }
        Matcher range = REVIEW_RANGE.matcher(text);
        if (range.matches()) {
            return new ParsedMessage(
                    ConversationMode.REVIEW,
                    questionOrDefault(range.group("question")),
                    range.group("base"),
                    range.group("head"),
                    environment,
                    false
            );
        }
        Matcher explicit = REVIEW_EXPLICIT.matcher(text);
        if (explicit.matches()) {
            return new ParsedMessage(
                    ConversationMode.REVIEW,
                    questionOrDefault(explicit.group("question")),
                    explicit.group("base"),
                    explicit.group("head"),
                    environment,
                    false
            );
        }
        if (hasReviewIntent(text)) {
            BranchPair pair = extractBranches(text);
            if (pair != null) {
                return new ParsedMessage(
                        ConversationMode.REVIEW,
                        text,
                        pair.base(),
                        pair.head(),
                        environment,
                        false
                );
            }
            if (startsWithReviewKeyword(text)) {
                return new ParsedMessage(
                        ConversationMode.REVIEW,
                        "请按默认分支对比规则审查差异。",
                        null,
                        null,
                        environment,
                        false
                );
            }
            // 自然语言说了要 review，但没抽出分支 → 仍走审查，让 Bot 提示补分支，绝不落入排查
            return new ParsedMessage(ConversationMode.REVIEW, text, null, null, environment, false);
        }
        return new ParsedMessage(ConversationMode.INVESTIGATE, text, null, null, environment, false);
    }

    static boolean hasReviewIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (startsWithReviewKeyword(text)) {
            return true;
        }
        if (REVIEW_PHRASE.matcher(text).find()) {
            return true;
        }
        // 「审查/review」出现在句中时，必须同时有分支信号，避免把「审查下 5xx」误判成代码审查
        return REVIEW_VERB.matcher(text).find() && BRANCH_SIGNAL.matcher(text).find();
    }

    static BranchPair extractBranches(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher rangeInline = REVIEW_RANGE_INLINE.matcher(text);
        if (rangeInline.find()) {
            return ordered(rangeInline.group("base"), rangeInline.group("head"), false);
        }
        Matcher explicitInline = REVIEW_EXPLICIT_INLINE.matcher(text);
        if (explicitInline.find()) {
            return ordered(explicitInline.group("base"), explicitInline.group("head"), false);
        }
        Matcher twoCn = TWO_BRANCH_CN.matcher(text);
        if (twoCn.find()) {
            return ordered(twoCn.group("a"), twoCn.group("b"), true);
        }
        Matcher pull = PULL_COMPARE.matcher(text);
        if (pull.find()) {
            return ordered(pull.group("a"), pull.group("b"), true);
        }
        Matcher vs = VS_RANGE.matcher(text);
        if (vs.find()) {
            return ordered(vs.group("a"), vs.group("b"), true);
        }
        Matcher dots = DOT_RANGE.matcher(text);
        if (dots.find()) {
            return ordered(dots.group("a"), dots.group("b"), false);
        }
        return null;
    }

    /**
     * @param inferStable 自然语言「A 和 B」时：稳定分支作 base、另一侧作 head；
     *                    显式 A..B / base/head 时保持书写顺序。
     */
    static BranchPair ordered(String left, String right, boolean inferStable) {
        String a = sanitizeRef(left);
        String b = sanitizeRef(right);
        if (a.isBlank() || b.isBlank() || a.equalsIgnoreCase(b)) {
            return null;
        }
        if (inferStable) {
            boolean aStable = isStableRef(a);
            boolean bStable = isStableRef(b);
            if (aStable && !bStable) {
                return new BranchPair(a, b);
            }
            if (bStable && !aStable) {
                return new BranchPair(b, a);
            }
        }
        return new BranchPair(a, b);
    }

    static boolean isStableRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        String name = ref.trim();
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        return STABLE_REFS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static String sanitizeRef(String ref) {
        if (ref == null) {
            return "";
        }
        return ref.trim().replaceAll("^[,，、]+|[,，、]+$", "");
    }

    static EnvPrefix stripEnvironmentPrefix(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            return new EnvPrefix(null, "");
        }
        Matcher compact = COMPACT_ENV.matcher(text);
        if (compact.matches()) {
            Environment env = parseAlias(compact.group("env"));
            if (env != null) {
                return new EnvPrefix(env, compact.group("rest").trim());
            }
        }
        Matcher labeled = LABELED_ENV.matcher(text);
        if (labeled.matches()) {
            Environment env = parseAlias(labeled.group("env"));
            if (env != null) {
                return new EnvPrefix(env, labeled.group("rest").trim());
            }
        }
        return new EnvPrefix(null, text);
    }

    static Environment parseAlias(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "test", "local", "测试" -> Environment.TEST;
            case "pre", "gray", "preview", "staging", "预发", "灰度" -> Environment.PRE;
            case "prod", "production", "prd", "生产" -> Environment.PROD;
            default -> null;
        };
    }

    private static boolean isReset(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("新对话")
                || normalized.equals("重置")
                || normalized.equals("reset");
    }

    private static boolean startsWithReviewKeyword(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("审查") || normalized.startsWith("review") || normalized.startsWith("#review");
    }

    private static String questionOrDefault(String question) {
        return question == null || question.isBlank() ? "请审查这两个分支的差异。" : question.trim();
    }

    record EnvPrefix(Environment environment, String text) {
    }

    record BranchPair(String base, String head) {
    }
}
