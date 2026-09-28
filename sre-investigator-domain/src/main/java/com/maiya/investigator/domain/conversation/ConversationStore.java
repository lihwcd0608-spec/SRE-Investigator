package com.maiya.investigator.domain.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationStore {
    ConversationThread save(ConversationThread thread);

    Optional<ConversationThread> findById(UUID id);

    List<ConversationThread> listByProject(UUID projectId);

    List<ConversationThread> listByProjectAndOrigin(UUID projectId, ConversationOrigin origin);

    ConversationMessage saveMessage(ConversationMessage message);

    List<ConversationMessage> messages(UUID conversationId);

    default List<ConversationMessage> recentMessages(UUID conversationId, int limit) {
        List<ConversationMessage> all = messages(conversationId);
        if (limit <= 0 || all.size() <= limit) {
            return all;
        }
        return List.copyOf(all.subList(all.size() - limit, all.size()));
    }

    Optional<ConversationMessage> findByRunId(UUID runId);

    long userTurnCount(UUID conversationId);

    void deleteByProjectId(UUID projectId);
}
