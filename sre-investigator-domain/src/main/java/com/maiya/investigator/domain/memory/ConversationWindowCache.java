package com.maiya.investigator.domain.memory;

import com.maiya.investigator.domain.conversation.ConversationMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationWindowCache {
    Optional<List<ConversationMessage>> get(UUID conversationId);

    void put(UUID conversationId, List<ConversationMessage> messages);

    static ConversationWindowCache noop() {
        return new ConversationWindowCache() {
            @Override
            public Optional<List<ConversationMessage>> get(UUID conversationId) {
                return Optional.empty();
            }

            @Override
            public void put(UUID conversationId, List<ConversationMessage> messages) {
            }
        };
    }
}
