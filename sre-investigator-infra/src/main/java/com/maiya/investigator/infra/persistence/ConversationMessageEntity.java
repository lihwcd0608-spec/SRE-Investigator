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
@Table(name = "investigator_message")
public class ConversationMessageEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID conversationId;

    @Column(name = "message_role", nullable = false, length = 20)
    private String role;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "run_id", columnDefinition = "BINARY(16)")
    private UUID runId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationMessageEntity() {
    }

    public ConversationMessageEntity(
            UUID id,
            UUID conversationId,
            String role,
            String content,
            UUID runId,
            Instant createdAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.runId = runId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public UUID getRunId() {
        return runId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
