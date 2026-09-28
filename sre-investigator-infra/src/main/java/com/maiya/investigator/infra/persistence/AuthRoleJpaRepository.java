package com.maiya.investigator.infra.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRoleJpaRepository extends JpaRepository<AuthRoleEntity, UUID> {
    Optional<AuthRoleEntity> findByCode(String code);
}
