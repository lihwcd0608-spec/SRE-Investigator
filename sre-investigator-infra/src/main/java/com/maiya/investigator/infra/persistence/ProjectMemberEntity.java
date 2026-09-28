package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "investigator_project_member")
@IdClass(ProjectMemberEntity.Key.class)
public class ProjectMemberEntity {
    @Id
    @Column(name = "project_id", columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectMemberEntity() {
    }

    public ProjectMemberEntity(UUID projectId, UUID userId, Instant createdAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static class Key implements Serializable {
        private UUID projectId;
        private UUID userId;

        public Key() {
        }

        public Key(UUID projectId, UUID userId) {
            this.projectId = projectId;
            this.userId = userId;
        }

        public UUID getProjectId() {
            return projectId;
        }

        public void setProjectId(UUID projectId) {
            this.projectId = projectId;
        }

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(projectId, key.projectId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, userId);
        }
    }
}
