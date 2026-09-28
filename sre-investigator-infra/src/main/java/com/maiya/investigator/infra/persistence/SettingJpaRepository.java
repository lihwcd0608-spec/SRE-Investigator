package com.maiya.investigator.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingJpaRepository extends JpaRepository<SettingEntity, String> {
}
