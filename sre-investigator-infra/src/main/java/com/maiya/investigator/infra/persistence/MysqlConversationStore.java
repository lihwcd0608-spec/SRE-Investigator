package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.conversation.ConversationMessage;
import com.maiya.investigator.domain.conversation.ConversationOrigin;
import com.maiya.investigator.domain.conversation.ConversationStore;
import com.maiya.investigator.domain.conversation.ConversationThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MysqlConversationStore implements ConversationStore {
    private final ConversationJpaRepository conversations;
    private final ConversationMessageJpaRepository messages;

    public MysqlConversationStore(ConversationJpaRepository conversations, ConversationMessageJpaRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Override
    public ConversationThread save(ConversationThread thread) {
        return toDomain(conversations.save(new ConversationEntity(
                thread.id(),
                thread.projectId(),
                thread.environment().code(),
                thread.mode().name(),
                thread.origin().name(),
                thread.title(),
                thread.memorySummary(),
                thread.createdByUserId(),
                thread.createdAt(),
                thread.updatedAt()
        )));
    }

    @Override
    public Optional<ConversationThread> findById(UUID id) {
        return conversations.findById(id).map(MysqlConversationStore::toDomain);
    }

    @Override
    public List<ConversationThread> listByProject(UUID projectId) {
        return listByProjectAndOrigin(projectId, ConversationOrigin.CONVERSATION);
    }

    @Override
    public List<ConversationThread> listByProjectAndOrigin(UUID projectId, ConversationOrigin origin) {
        ConversationOrigin wanted = origin == null ? ConversationOrigin.CONVERSATION : origin;
        return conversations.findByProjectIdAndOriginOrderByUpdatedAtDesc(projectId, wanted.name())
                .stream()
                .map(MysqlConversationStore::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public ConversationMessage saveMessage(ConversationMessage message) {
        return toMessage(messages.save(new ConversationMessageEntity(
                message.id(),
                message.conversationId(),
                message.role(),
                message.content(),
                message.runId(),
                message.createdAt()
        )));
    }

    @Override
    public List<ConversationMessage> messages(UUID conversationId) {
        return messages.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(MysqlConversationStore::toMessage)
                .toList();
    }

    @Override
    public Optional<ConversationMessage> findByRunId(UUID runId) {
        return messages.findFirstByRunId(runId).map(MysqlConversationStore::toMessage);
    }

    @Override
    public long userTurnCount(UUID conversationId) {
        return messages.countByConversationIdAndRole(conversationId, "user");
    }

    @Override
    @Transactional
    public void deleteByProjectId(UUID projectId) {
        List<UUID> ids = conversations.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(ConversationEntity::getId)
                .toList();
        if (!ids.isEmpty()) {
            messages.deleteByConversationIdIn(ids);
        }
        conversations.deleteByProjectId(projectId);
    }

    private static ConversationThread toDomain(ConversationEntity entity) {
        return new ConversationThread(
                entity.getId(),
                entity.getProjectId(),
                Environment.from(entity.getEnvironment()),
                ConversationMode.from(entity.getConversationMode()),
                ConversationOrigin.from(entity.getOrigin()),
                entity.getTitle(),
                entity.getMemorySummary(),
                entity.getCreatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static ConversationMessage toMessage(ConversationMessageEntity entity) {
        return new ConversationMessage(
                entity.getId(),
                entity.getConversationId(),
                entity.getRole(),
                entity.getContent(),
                entity.getRunId(),
                entity.getCreatedAt()
        );
    }
}
