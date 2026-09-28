package com.maiya.investigator.graph.route;

import com.maiya.investigator.domain.model.ModelProvider;
import com.maiya.investigator.domain.model.ModelSettingsResolver;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.graph.ChatModelFactory;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatRouteModelClassifier implements RouteModelClassifier {
    private static final Logger log = LoggerFactory.getLogger(ChatRouteModelClassifier.class);
    private final ModelSettingsResolver modelSettings;
    private final ChatModelFactory chatModels;

    public ChatRouteModelClassifier(ModelSettingsResolver modelSettings, ChatModelFactory chatModels) {
        this.modelSettings = modelSettings;
        this.chatModels = chatModels;
    }

    @Override
    public Optional<InvestigationRoute> classify(
            String question,
            List<InvestigationRoute> routes,
            String provider,
            String model
    ) {
        if (question == null || question.isBlank() || routes == null || routes.isEmpty()) {
            return Optional.empty();
        }
        ResolvedModelSettings settings = resolveSettings(provider, model);
        if (!settings.available()) {
            log.info("方向分类跳过模型：{}", settings.missingReason());
            return Optional.empty();
        }
        try {
            String reply = ChatClient.create(chatModels.create(settings))
                    .prompt()
                    .system("你是排查方向分类器。只从用户给出的方向列表中选一个 id。只输出该 id，不要解释，不要输出其他文字。")
                    .user(prompt(question, routes))
                    .call()
                    .content();
            Optional<InvestigationRoute> parsed = InvestigationRouteMatcher.parseModelReply(reply, routes);
            log.info(
                    "方向分类模型 provider={} model={} reply={} parsed={}",
                    settings.provider().id(),
                    settings.model(),
                    preview(reply),
                    parsed.map(InvestigationRoute::id).orElse("")
            );
            return parsed;
        } catch (Exception exception) {
            log.warn("方向分类模型失败，将回退关键词：{}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String prompt(String question, List<InvestigationRoute> routes) {
        String options = routes.stream()
                .map(item -> "- " + item.id() + "：" + item.label() + "。" + nullToEmpty(item.description()))
                .collect(Collectors.joining("\n"));
        return """
                方向列表：
                %s

                问题：
                %s
                """.formatted(options, question);
    }

    private ResolvedModelSettings resolveSettings(String provider, String modelName) {
        if ((provider == null || provider.isBlank()) && (modelName == null || modelName.isBlank())) {
            return modelSettings.current();
        }
        ModelProvider type = provider == null || provider.isBlank()
                ? modelSettings.current().provider()
                : ModelProvider.from(provider);
        return modelSettings.resolve(type, modelName, null, null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 80) + "…" : compact;
    }
}
