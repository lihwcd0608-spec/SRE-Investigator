package com.maiya.investigator.infra.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationJobJpaRepository extends JpaRepository<AutomationJobEntity, UUID> {
    List<AutomationJobEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<AutomationJobEntity> findByProjectIdInOrderByUpdatedAtDesc(List<UUID> projectIds);

    List<AutomationJobEntity> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);

    void deleteByProjectId(UUID projectId);
}
