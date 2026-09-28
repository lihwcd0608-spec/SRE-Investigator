package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.domain.model.SettingStore;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MysqlSettingStore implements SettingStore {
    private final SettingJpaRepository settings;

    public MysqlSettingStore(SettingJpaRepository settings) {
        this.settings = settings;
    }

    @Override
    public Optional<String> get(String key) {
        return settings.findById(key).map(SettingEntity::getEncryptedValue);
    }

    @Override
    public void put(String key, String value) {
        SettingEntity entity = settings.findById(key)
                .orElseGet(() -> new SettingEntity(key, value, Instant.now()));
        entity.setEncryptedValue(value);
        entity.setUpdatedAt(Instant.now());
        settings.save(entity);
    }
}
