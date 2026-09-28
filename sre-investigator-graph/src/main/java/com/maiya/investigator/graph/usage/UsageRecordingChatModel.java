package com.maiya.investigator.graph.usage;

import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.domain.usage.ModelUsageEvent;
import com.maiya.investigator.domain.usage.ModelUsageSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class UsageRecordingChatModel implements ChatModel {
    private static final Logger log = LoggerFactory.getLogger(UsageRecordingChatModel.class);
    private final ChatModel delegate;
    private final ResolvedModelSettings settings;
    private final ModelUsageSink sink;

    public UsageRecordingChatModel(ChatModel delegate, ResolvedModelSettings settings, ModelUsageSink sink) {
        this.delegate = delegate;
        this.settings = settings;
        this.sink = sink;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse response = delegate.call(prompt);
        recordQuietly(response);
        return response;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(prompt);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    private void recordQuietly(ChatResponse response) {
        try {
            UsageSnapshot actor = UsageContext.current();
            if (actor == null || sink == null) {
                return;
            }
            TokenUsage tokens = readUsage(response);
            sink.accept(ModelUsageEvent.of(
                    actor.userId(),
                    actor.username(),
                    actor.roleCodesCsv(),
                    actor.projectId(),
                    settings.provider().id(),
                    settings.model(),
                    tokens.prompt,
                    tokens.completion,
                    tokens.total
            ));
        } catch (RuntimeException exception) {
            log.warn("用量记录失败：{}", exception.getMessage());
        }
    }

    static TokenUsage readUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return TokenUsage.unknown();
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return TokenUsage.unknown();
        }
        Long prompt = toLong(usage::getPromptTokens);
        Long completion = toLong(usage::getCompletionTokens);
        Long total = toLong(usage::getTotalTokens);
        return new TokenUsage(prompt, completion, total);
    }

    private static Long toLong(java.util.function.Supplier<Integer> getter) {
        try {
            Integer value = getter.get();
            return value == null ? null : value.longValue();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    record TokenUsage(Long prompt, Long completion, Long total) {
        static TokenUsage unknown() {
            return new TokenUsage(null, null, null);
        }
    }
}
