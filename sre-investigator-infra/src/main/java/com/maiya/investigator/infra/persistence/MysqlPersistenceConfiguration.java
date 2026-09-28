package com.maiya.investigator.infra.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Profile("mysql")
@EntityScan(basePackages = "com.maiya.investigator.infra")
@EnableJpaRepositories(basePackages = "com.maiya.investigator.infra.persistence")
public class MysqlPersistenceConfiguration {
}
