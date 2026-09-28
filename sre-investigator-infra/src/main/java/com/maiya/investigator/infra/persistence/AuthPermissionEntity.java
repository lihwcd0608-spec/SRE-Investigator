package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "investigator_permission")
public class AuthPermissionEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "perm_type", nullable = false, length = 20)
    private String type;

    @Column(name = "menu_path", length = 200)
    private String menuPath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected AuthPermissionEntity() {
    }

    public AuthPermissionEntity(UUID id, String code, String name, String type, String menuPath, int sortOrder) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.menuPath = menuPath;
        this.sortOrder = sortOrder;
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

    public String getType() {
        return type;
    }

    public String getMenuPath() {
        return menuPath;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
