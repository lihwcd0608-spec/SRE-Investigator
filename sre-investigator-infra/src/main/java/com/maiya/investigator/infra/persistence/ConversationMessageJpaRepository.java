package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageJpaRepository extends JpaRepository<ConversationMessageEntity, UUID> {
    List<ConversationMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    Optional<ConversationMessageEntity> findFirstByRunId(UUID runId);

    long countByConversationIdAndRole(UUID conversationId, String role);

    void deleteByConversationIdIn(List<UUID> conversationIds);
}
