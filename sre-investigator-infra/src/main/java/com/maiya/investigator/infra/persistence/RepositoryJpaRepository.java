package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, UUID> {
    List<RepositoryEntity> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
