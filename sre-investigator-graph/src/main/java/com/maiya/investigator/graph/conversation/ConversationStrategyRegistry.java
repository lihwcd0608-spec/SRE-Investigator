package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.api.ConversationMode;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConversationStrategyRegistry {
    private final Map<ConversationMode, ConversationStrategy> strategies = new EnumMap<>(ConversationMode.class);

    public ConversationStrategyRegistry(List<ConversationStrategy> implementations) {
        for (ConversationStrategy strategy : implementations) {
            strategies.put(strategy.mode(), strategy);
        }
    }

    public ConversationStrategy require(ConversationMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("对话模式不能为空");
        }
        if (!mode.enabled()) {
            throw new IllegalStateException(mode.label() + "尚未开放：" + mode.description());
        }
        ConversationStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new IllegalStateException("未注册对话策略：" + mode);
        }
        return strategy;
    }

    public List<ModeDescriptor> descriptors() {
        return Arrays.stream(ConversationMode.values())
                .filter(mode -> mode != ConversationMode.REVIEW)
                .map(mode -> new ModeDescriptor(mode.name(), mode.label(), mode.description(), mode.enabled()))
                .toList();
    }

    public record ModeDescriptor(String id, String label, String description, boolean enabled) {
    }
}
