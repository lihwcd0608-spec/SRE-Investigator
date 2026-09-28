package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {
    List<ConversationEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<ConversationEntity> findByProjectIdAndOriginOrderByUpdatedAtDesc(UUID projectId, String origin);

    void deleteByProjectId(UUID projectId);
}
