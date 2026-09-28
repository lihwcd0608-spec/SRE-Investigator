package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigator_repository")
public class RepositoryEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "repository_role", nullable = false)
    private String role;

    @Column(name = "root_path", nullable = false, length = 1024)
    private String rootPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "git_url", length = 1024)
    private String gitUrl;

    @Column(name = "git_ref", length = 200)
    private String gitRef;

    protected RepositoryEntity() {
    }

    public RepositoryEntity(
            UUID id,
            UUID projectId,
            String name,
            String role,
            String rootPath,
            Instant createdAt,
            String gitUrl,
            String gitRef
    ) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.role = role;
        this.rootPath = rootPath;
        this.createdAt = createdAt;
        this.gitUrl = gitUrl;
        this.gitRef = gitRef;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getRootPath() {
        return rootPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public String getGitRef() {
        return gitRef;
    }
}
