package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "investigator_role")
public class AuthRoleEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean builtin;

    @Column(length = 400)
    private String description;

    protected AuthRoleEntity() {
    }

    public AuthRoleEntity(UUID id, String code, String name, boolean builtin, String description) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.builtin = builtin;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public String getDescription() {
        return description;
    }
}
