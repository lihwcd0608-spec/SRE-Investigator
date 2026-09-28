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
@Table(name = "investigator_conversation")
public class ConversationEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false)
    private String environment;

    @Column(name = "conversation_mode", nullable = false, length = 40)
    private String conversationMode;

    @Column(nullable = false, length = 20)
    private String origin;

    @Column(length = 512)
    private String title;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "memory_summary", columnDefinition = "LONGTEXT")
    private String memorySummary;

    @Column(name = "created_by_user_id", columnDefinition = "BINARY(16)")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {
    }

    public ConversationEntity(
            UUID id,
            UUID projectId,
            String environment,
            String conversationMode,
            String origin,
            String title,
            String memorySummary,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.environment = environment;
        this.conversationMode = conversationMode;
        this.origin = origin;
        this.title = title;
        this.memorySummary = memorySummary;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getConversationMode() {
        return conversationMode;
    }

    public String getOrigin() {
        return origin;
    }

    public String getTitle() {
        return title;
    }

    public String getMemorySummary() {
        return memorySummary;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
