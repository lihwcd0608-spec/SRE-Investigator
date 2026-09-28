package com.maiya.investigator.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigator_project")
public class ProjectEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String topology;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectEntity() {
    }

    public ProjectEntity(UUID id, String slug, String name, String topology, Instant createdAt) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.topology = topology;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getTopology() {
        return topology;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
