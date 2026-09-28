package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "investigator_setting")
public class SettingEntity {
    @Id
    @Column(name = "setting_key", nullable = false, length = 80)
    private String settingKey;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "encrypted_value", nullable = false, columnDefinition = "LONGTEXT")
    private String encryptedValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SettingEntity() {
    }

    public SettingEntity(String settingKey, String encryptedValue, Instant updatedAt) {
        this.settingKey = settingKey;
        this.encryptedValue = encryptedValue;
        this.updatedAt = updatedAt;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setEncryptedValue(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
