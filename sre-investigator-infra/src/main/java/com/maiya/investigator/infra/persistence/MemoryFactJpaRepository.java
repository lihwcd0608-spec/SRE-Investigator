package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryFactJpaRepository extends JpaRepository<MemoryFactEntity, UUID> {
    List<MemoryFactEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
