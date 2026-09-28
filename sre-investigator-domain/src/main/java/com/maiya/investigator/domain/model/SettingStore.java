package com.maiya.investigator.domain.model;

import java.util.Optional;

public interface SettingStore {
    Optional<String> get(String key);

    void put(String key, String value);
}
