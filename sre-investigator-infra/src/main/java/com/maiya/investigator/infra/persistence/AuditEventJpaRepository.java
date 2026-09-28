package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findTop200ByOrderByOccurredAtDesc();

    List<AuditEventEntity> findTop200ByCategoryOrderByOccurredAtDesc(String category);
}
