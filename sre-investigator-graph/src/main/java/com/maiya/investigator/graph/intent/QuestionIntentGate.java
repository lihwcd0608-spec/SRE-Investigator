package com.maiya.investigator.graph.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.domain.model.ModelProvider;
import com.maiya.investigator.domain.model.ModelSettingsResolver;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.graph.ChatModelFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class QuestionIntentGate {
    private static final Logger log = LoggerFactory.getLogger(QuestionIntentGate.class);

    private final ModelSettingsResolver modelSettings;
    private final ChatModelFactory chatModels;
    private final ObjectMapper mapper;
    private final Map<String, QuestionIntent> recent = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, QuestionIntent> eldest) {
            return size() > 64;
        }
    });

    public QuestionIntentGate(
            ModelSettingsResolver modelSettings,
            ChatModelFactory chatModels,
            ObjectMapper mapper
    ) {
        this.modelSettings = modelSettings;
        this.chatModels = chatModels;
        this.mapper = mapper;
    }

    public QuestionIntent evaluate(ConversationMode mode, String question) {
        return evaluate(mode, question, false);
    }

    /**
     * @param followUp 同一会话续问（飞书线程未过期 / 对话 turn>1）。续问默认放行，避免短指令被模型误判成闲聊。
     */
    public QuestionIntent evaluate(ConversationMode mode, String question, boolean followUp) {
        if (mode == ConversationMode.REVIEW) {
            return QuestionIntent.go();
        }
        String cacheKey = (mode == null ? "INVESTIGATE" : mode.name())
                + (followUp ? ":follow:" : ":")
                + QuestionIntentHeuristics.compact(question);
        QuestionIntent cached = recent.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        QuestionIntentHeuristics.Hint hint = QuestionIntentHeuristics.hint(question);
        if (hint == QuestionIntentHeuristics.Hint.ALLOW) {
            log.info("问句前置过滤放行 heuristic=ALLOW followUp={} question={}", followUp, preview(question));
            return remember(cacheKey, QuestionIntent.go());
        }
        if (hint == QuestionIntentHeuristics.Hint.CHAT) {
            String reply = QuestionIntentHeuristics.replyFor(question, hint);
            log.info("问句前置过滤闲聊回应 heuristic=CHAT question={} reply={}", preview(question), preview(reply));
            return remember(cacheKey, QuestionIntent.skip(reply));
        }
        if (followUp) {
            // 续问：只拦空句/纯灌水，其它短指令（结论、继续、详细点）一律进会话
            if (hint == QuestionIntentHeuristics.Hint.REJECT && QuestionIntentHeuristics.isPureFiller(question)) {
                String reply = QuestionIntentHeuristics.replyFor(question, hint);
                log.info("问句前置过滤续问灌水拦截 question={}", preview(question));
                return remember(cacheKey, QuestionIntent.skip(reply));
            }
            log.info("问句前置过滤续问放行 heuristic={} question={}", hint, preview(question));
            return remember(cacheKey, QuestionIntent.go());
        }
        if (hint == QuestionIntentHeuristics.Hint.REJECT) {
            String reply = QuestionIntentHeuristics.replyFor(question, hint);
            log.info("问句前置过滤拦截 heuristic=REJECT question={}", preview(question));
            return remember(cacheKey, QuestionIntent.skip(reply));
        }
        QuestionIntent modelDecision = classifyWithModel(question, false);
        if (modelDecision != null) {
            log.info(
                    "问句前置过滤模型 heuristic=UNSURE proceed={} question={}",
                    modelDecision.proceed(),
                    preview(question)
            );
            return remember(cacheKey, modelDecision);
        }
        log.info("问句前置过滤模型不可用，放行以免误伤排查 question={}", preview(question));
        return remember(cacheKey, QuestionIntent.go());
    }

    private QuestionIntent remember(String key, QuestionIntent intent) {
        recent.put(key, intent);
        return intent;
    }

    private QuestionIntent classifyWithModel(String question, boolean followUp) {
        ResolvedModelSettings settings = modelSettings.current();
        if (!settings.available()) {
            log.info("问句前置过滤跳过模型：{}", settings.missingReason());
            return null;
        }
        try {
            String system = followUp
                    ? """
                            你是飞书群里的 SRE 助手入口。用户正在同一排查会话里续问。
                            只输出一个 JSON 对象，不要 Markdown，不要解释。
                            续问、要结论、要展开、要继续查：{"action":"go"}
                            夸奖、感谢：{"action":"skip","reply":"一两句自然口语回应"}
                            只有明显无关灌水才 skip。
                            """
                    : """
                            你是飞书群里的 SRE 助手入口。判断用户这句话要不要开只读排查或代码审查。
                            只输出一个 JSON 对象，不要 Markdown，不要解释。
                            要排查或审查时：{"action":"go"}
                            夸奖、感谢、点赞、寒暄：{"action":"skip","reply":"一两句自然口语回应，像同事，不要客服腔，不要开始排查"}
                            辱骂、玩笑、无故障信息的试探：{"action":"skip","reply":"简短引导补环境、现象、用户/订单号或审查分支"}
                            不要回骂，不要重复脏话，不要说「这不像排查」这种生硬句，除非完全无法判断意图。
                            """;
            String reply = ChatClient.create(chatModels.create(settings))
                    .prompt()
                    .system(system)
                    .user("问题：\n" + QuestionIntentHeuristics.compact(question))
                    .call()
                    .content();
            return QuestionIntentParser.parseModelReply(reply, mapper).orElse(null);
        } catch (Exception exception) {
            log.warn("问句前置过滤模型失败，将放行：{}", exception.getMessage());
            return null;
        }
    }

    private static String preview(String text) {
        String compact = QuestionIntentHeuristics.compact(text);
        return compact.length() > 80 ? compact.substring(0, 80) + "…" : compact;
    }
}
