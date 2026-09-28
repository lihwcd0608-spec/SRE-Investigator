package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Key> {
    List<UserRoleEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    void deleteByRoleId(UUID roleId);
}
