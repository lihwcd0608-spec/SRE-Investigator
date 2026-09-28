package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "investigator_memory_fact")
public class MemoryFactEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false, length = 40)
    private String kind;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_conversation_id", columnDefinition = "BINARY(16)")
    private UUID sourceConversationId;

    @Column(name = "qdrant_point_id", columnDefinition = "BINARY(16)")
    private UUID qdrantPointId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemoryFactEntity() {
    }

    public MemoryFactEntity(
            UUID id,
            UUID projectId,
            String kind,
            String content,
            UUID sourceConversationId,
            UUID qdrantPointId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.kind = kind;
        this.content = content;
        this.sourceConversationId = sourceConversationId;
        this.qdrantPointId = qdrantPointId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }

    public UUID getSourceConversationId() {
        return sourceConversationId;
    }

    public UUID getQdrantPointId() {
        return qdrantPointId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
