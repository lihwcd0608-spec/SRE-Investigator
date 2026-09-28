package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "investigator_role_permission")
@IdClass(RolePermissionEntity.Key.class)
public class RolePermissionEntity {
    @Id
    @Column(name = "role_id", columnDefinition = "BINARY(16)")
    private UUID roleId;

    @Id
    @Column(name = "permission_id", columnDefinition = "BINARY(16)")
    private UUID permissionId;

    protected RolePermissionEntity() {
    }

    public RolePermissionEntity(UUID roleId, UUID permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public static class Key implements Serializable {
        private UUID roleId;
        private UUID permissionId;

        public Key() {
        }

        public Key(UUID roleId, UUID permissionId) {
            this.roleId = roleId;
            this.permissionId = permissionId;
        }

        public UUID getRoleId() {
            return roleId;
        }

        public void setRoleId(UUID roleId) {
            this.roleId = roleId;
        }

        public UUID getPermissionId() {
            return permissionId;
        }

        public void setPermissionId(UUID permissionId) {
            this.permissionId = permissionId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(roleId, key.roleId) && Objects.equals(permissionId, key.permissionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, permissionId);
        }
    }
}
