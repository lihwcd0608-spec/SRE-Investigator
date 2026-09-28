package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionJpaRepository extends JpaRepository<RolePermissionEntity, RolePermissionEntity.Key> {
    List<RolePermissionEntity> findByRoleId(UUID roleId);

    void deleteByRoleId(UUID roleId);
}
