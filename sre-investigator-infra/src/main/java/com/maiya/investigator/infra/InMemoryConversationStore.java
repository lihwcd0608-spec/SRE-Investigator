package com.maiya.investigator.infra;

import com.maiya.investigator.domain.conversation.ConversationMessage;
import com.maiya.investigator.domain.conversation.ConversationOrigin;
import com.maiya.investigator.domain.conversation.ConversationStore;
import com.maiya.investigator.domain.conversation.ConversationThread;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryConversationStore implements ConversationStore {
    private final ConcurrentHashMap<UUID, ConversationThread> threads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConversationMessage> messages = new ConcurrentHashMap<>();

    @Override
    public ConversationThread save(ConversationThread thread) {
        threads.put(thread.id(), thread);
        return thread;
    }

    @Override
    public Optional<ConversationThread> findById(UUID id) {
        return Optional.ofNullable(threads.get(id));
    }

    @Override
    public List<ConversationThread> listByProject(UUID projectId) {
        return listByProjectAndOrigin(projectId, ConversationOrigin.CONVERSATION);
    }

    @Override
    public List<ConversationThread> listByProjectAndOrigin(UUID projectId, ConversationOrigin origin) {
        ConversationOrigin wanted = origin == null ? ConversationOrigin.CONVERSATION : origin;
        return threads.values().stream()
                .filter(thread -> thread.projectId().equals(projectId))
                .filter(thread -> thread.origin() == wanted)
                .sorted(Comparator.comparing(ConversationThread::updatedAt).reversed())
                .toList();
    }

    @Override
    public ConversationMessage saveMessage(ConversationMessage message) {
        messages.put(message.id(), message);
        return message;
    }

    @Override
    public List<ConversationMessage> messages(UUID conversationId) {
        return messages.values().stream()
                .filter(message -> message.conversationId().equals(conversationId))
                .sorted(Comparator.comparing(ConversationMessage::createdAt))
                .toList();
    }

    @Override
    public Optional<ConversationMessage> findByRunId(UUID runId) {
        return messages.values().stream()
                .filter(message -> runId.equals(message.runId()))
                .findFirst();
    }

    @Override
    public long userTurnCount(UUID conversationId) {
        return messages.values().stream()
                .filter(message -> message.conversationId().equals(conversationId) && "user".equals(message.role()))
                .count();
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        List<UUID> ids = threads.values().stream()
                .filter(thread -> thread.projectId().equals(projectId))
                .map(ConversationThread::id)
                .toList();
        threads.values().removeIf(thread -> thread.projectId().equals(projectId));
        messages.values().removeIf(message -> ids.contains(message.conversationId()));
    }
}
