package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "investigator_user_identity")
public class UserIdentityEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    protected UserIdentityEntity() {
    }

    public UserIdentityEntity(UUID id, UUID userId, String provider, String externalId) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.externalId = externalId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }
}
