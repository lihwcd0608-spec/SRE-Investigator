package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.api.ConversationMode;
import org.springframework.stereotype.Component;

@Component
public class CodeConversationStrategy implements ConversationStrategy {
    @Override
    public ConversationMode mode() {
        return ConversationMode.CODE;
    }

    @Override
    public ConversationOutcome execute(ConversationContext context) {
        throw new IllegalStateException("写代码模式尚未开放，当前只支持提问和排查问题。");
    }
}
