package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationRunJpaRepository extends JpaRepository<InvestigationRunEntity, UUID> {
    List<InvestigationRunEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
