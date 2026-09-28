package com.maiya.investigator.infra.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserJpaRepository extends JpaRepository<AuthUserEntity, UUID> {
    Optional<AuthUserEntity> findByUsername(String username);
}
