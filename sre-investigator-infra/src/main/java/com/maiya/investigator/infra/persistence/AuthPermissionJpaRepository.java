package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthPermissionJpaRepository extends JpaRepository<AuthPermissionEntity, UUID> {
    Optional<AuthPermissionEntity> findByCode(String code);

    List<AuthPermissionEntity> findAllByOrderBySortOrderAsc();
}
