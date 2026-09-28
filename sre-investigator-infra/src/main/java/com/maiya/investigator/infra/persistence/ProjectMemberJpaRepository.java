package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberEntity.Key> {
    List<ProjectMemberEntity> findByUserId(UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteByUserId(UUID userId);

    void deleteByProjectId(UUID projectId);
}
