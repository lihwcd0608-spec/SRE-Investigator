package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolBindingJpaRepository extends JpaRepository<ToolBindingEntity, UUID> {
    List<ToolBindingEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<ToolBindingEntity> findByMonitoringEnabledTrue();

    void deleteByProjectId(UUID projectId);
}
