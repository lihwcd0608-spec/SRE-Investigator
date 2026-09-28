package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.api.ConversationMode;

public interface ConversationStrategy {
    ConversationMode mode();

    ConversationOutcome execute(ConversationContext context);
}