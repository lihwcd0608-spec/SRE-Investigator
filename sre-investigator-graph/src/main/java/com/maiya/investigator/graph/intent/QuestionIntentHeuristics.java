package com.maiya.investigator.graph.intent;

import java.util.regex.Pattern;

public final class QuestionIntentHeuristics {
    static final String DEFAULT_SKIP_REPLY =
            "这不像排查或代码审查。请说明环境、故障现象，带上用户/订单号、报错或 traceId；代码审查请发：审查 main..feature/xxx。";

    private static final String[] PRAISE_REPLIES = {
            "谢谢，有新问题把环境、现象和用户/订单号丢过来就行。",
            "过奖了。下次直接说故障，或发：审查 main..feature/xxx。",
            "收到，后面排查或审查再 @ 我。"
    };
    private static final String[] GREETING_REPLIES = {
            "在的。说下环境、故障现象，带上用户/订单号；审查发：审查 main..feature/xxx。",
            "在。把报错、traceId 或订单号发我，我帮你查。"
    };
    private static final String[] FILLER_REPLIES = {
            "需要查的话直接说环境和现象，我开排查看一看。",
            "有故障就丢现象和用户/订单号；想看代码就发：审查 main..feature/xxx。"
    };

    private static final Pattern OPERATIONAL = Pattern.compile(
            "(?i)5xx|4xx|超时|报错|异常|失败|挂了|挂掉|崩了|宕机|订单|支付|充值|到账|回调|幂等|退款|"
                    + "用户|接口|网关|日志|redis|mysql|nacos|k8s|pod|traceid|trace-id|npe|exception|"
                    + "error|timeout|sqlstate|审查|review|排查|代码仓|mapper|controller|serviceimpl|"
                    + "nullpointer|stacktrace|\\d{5,}|com\\.[a-z0-9_.]+"
                    + "|[A-Z][A-Za-z0-9]+(Impl|Controller|Service|Mapper|Client)"
    );

    /** 同一会话里的短续问：有上下文，不能当闲聊拦掉。 */
    private static final Pattern CONTINUATION = Pattern.compile(
            "(?i)^(请)?(直接)?(说|给|出|写)?(一下|下)?结论(呢|吧|啊)?$"
                    + "|^(结论|根因|原因)(是什么|呢|是啥)?$"
                    + "|^(总结|归纳)(一下|下|下吧)?$"
                    + "|^(继续|接着)(说|查|看|排|分析)?(一下|下)?$"
                    + "|^(再|继续)?(详细|展开)(说|讲|一点|点|些)?$"
                    + "|^(然后呢|下一步|怎么修|怎么处理|怎么解决|再查一下|再看看|确认一下)$"
    );

    private static final Pattern PRAISE = Pattern.compile(
            "(?i)^(真?不错[哦啊呀勒]?(了)?|可以[啊哦的啦]?|厉害[了啦哦]?|真棒|真好|太强(了)?|太棒(了)?|"
                    + "牛[啊哦逼批]?|棒[啊哦]?|赞(了)?|给力|优秀|靠谱|稳(了)?|好用|聪明|智能|"
                    + "辛苦了|谢谢(你|啦|啊)?|感谢|多谢|thx|ty|thanks|thank you|nice( work)?|"
                    + "good( job)?|great|awesome|perfect|well done|yyds|6{2,}|不戳)[!！。. ]*$"
    );

    private static final Pattern GREETING = Pattern.compile(
            "(?i)^(hi+|hello+|hey+|yo+|哈喽|嗨|你好|早上好|中午好|晚上好|在吗|在么|在不在)$"
    );

    private static final Pattern FILLER = Pattern.compile(
            "(?i)^(ping|test|ok|哈+哈+|呵+呵+|嗯+|哦+|额+|好的|没事|随便|测试一下|"
                    + "你是谁|你能做什么|收到)$"
    );

    private static final Pattern DECORATION = Pattern.compile(
            "[\\p{So}\\p{Cn}\\p{Sk}\\x{FE0F}\\x{200D}]+"
    );

    enum Hint {
        ALLOW,
        REJECT,
        CHAT,
        UNSURE
    }

    private QuestionIntentHeuristics() {
    }

    // 本次新增：供工具收窄复用，判断问句是否带运维/数据故障信号
    public static boolean hasOperationalSignal(String question) {
        return OPERATIONAL.matcher(compact(question)).find();
    }

    static Hint hint(String question) {
        String text = compact(question);
        if (text.isEmpty()) {
            return Hint.REJECT;
        }
        if (OPERATIONAL.matcher(text).find()) {
            return Hint.ALLOW;
        }
        String core = coreText(text);
        if (CONTINUATION.matcher(core).matches() || CONTINUATION.matcher(text).matches()) {
            return Hint.ALLOW;
        }
        if (core.isEmpty() || PRAISE.matcher(core).matches() || PRAISE.matcher(text).matches()) {
            return Hint.CHAT;
        }
        if (GREETING.matcher(core).matches()) {
            return Hint.CHAT;
        }
        if (FILLER.matcher(core).matches()) {
            return Hint.REJECT;
        }
        int length = text.codePointCount(0, text.length());
        if (length <= 4) {
            return Hint.REJECT;
        }
        return Hint.UNSURE;
    }

    static boolean isContinuation(String question) {
        String text = compact(question);
        if (text.isEmpty()) {
            return false;
        }
        String core = coreText(text);
        return CONTINUATION.matcher(core).matches() || CONTINUATION.matcher(text).matches();
    }

    static boolean isPureFiller(String question) {
        String core = coreText(compact(question));
        return core.isEmpty() || FILLER.matcher(core).matches();
    }

    static String replyFor(String question, Hint hint) {
        if (hint == Hint.CHAT) {
            String core = coreText(compact(question));
            if (core.isEmpty() || PRAISE.matcher(core).matches()) {
                return pick(question, PRAISE_REPLIES);
            }
            if (GREETING.matcher(core).matches()) {
                return pick(question, GREETING_REPLIES);
            }
            return pick(question, PRAISE_REPLIES);
        }
        if (hint == Hint.REJECT) {
            String core = coreText(compact(question));
            if (FILLER.matcher(core).matches()) {
                return pick(question, FILLER_REPLIES);
            }
        }
        return DEFAULT_SKIP_REPLY;
    }

    static String compact(String question) {
        if (question == null) {
            return "";
        }
        return question.replaceAll("\\s+", " ").trim();
    }

    static String coreText(String question) {
        if (question == null) {
            return "";
        }
        return DECORATION.matcher(question).replaceAll("").replaceAll("[\\s~！!！,.。、]+", " ").trim();
    }

    private static String pick(String text, String[] options) {
        int index = Math.floorMod(compact(text).hashCode(), options.length);
        return options[index];
    }
}
