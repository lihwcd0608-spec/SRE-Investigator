package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionJpaRepository extends JpaRepository<AutomationExecutionEntity, UUID> {
    List<AutomationExecutionEntity> findByJobIdOrderByStartedAtDesc(UUID jobId);

    void deleteByJobId(UUID jobId);

    void deleteByJobIdIn(List<UUID> jobIds);
}
