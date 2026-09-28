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
@Table(name = "investigator_audit_event")
public class AuditEventEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "run_id", columnDefinition = "BINARY(16)")
    private UUID runId;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "actor_user_id", columnDefinition = "BINARY(16)")
    private UUID actorUserId;

    @Column(name = "actor_username", length = 80)
    private String actorUsername;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column
    private Boolean success;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false, length = 512)
    private String target;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "LONGTEXT")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(
            UUID id,
            UUID runId,
            String category,
            UUID actorUserId,
            String actorUsername,
            String clientIp,
            Boolean success,
            String action,
            String target,
            String metadata,
            Instant occurredAt
    ) {
        this.id = id;
        this.runId = runId;
        this.category = category == null || category.isBlank() ? "OP" : category;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.clientIp = clientIp;
        this.success = success;
        this.action = action;
        this.target = target;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
