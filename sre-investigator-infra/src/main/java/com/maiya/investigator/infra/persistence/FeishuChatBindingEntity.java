package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigator_feishu_chat_binding")
public class FeishuChatBindingEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "project_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;
    @Column(name = "chat_id", nullable = false, length = 128)
    private String chatId;
    @Column(name = "chat_name", nullable = false)
    private String chatName;
    @Column(nullable = false, length = 20)
    private String environment;
    @Column(name = "allowed_environments", nullable = false, length = 64)
    private String allowedEnvironments;
    @Column(name = "default_route", nullable = false, length = 64)
    private String defaultRoute;
    @Column(name = "default_repository_id", columnDefinition = "BINARY(16)")
    private UUID defaultRepositoryId;
    @Column(name = "last_conversation_id", columnDefinition = "BINARY(16)")
    private UUID lastConversationId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeishuChatBindingEntity() {
    }

    public FeishuChatBindingEntity(
            UUID id,
            UUID projectId,
            String chatId,
            String chatName,
            String environment,
            String allowedEnvironments,
            String defaultRoute,
            UUID defaultRepositoryId,
            UUID lastConversationId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.chatId = chatId;
        this.chatName = chatName;
        this.environment = environment;
        this.allowedEnvironments = allowedEnvironments;
        this.defaultRoute = defaultRoute;
        this.defaultRepositoryId = defaultRepositoryId;
        this.lastConversationId = lastConversationId;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getChatName() {
        return chatName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getAllowedEnvironments() {
        return allowedEnvironments;
    }

    public String getDefaultRoute() {
        return defaultRoute;
    }

    public UUID getDefaultRepositoryId() {
        return defaultRepositoryId;
    }

    public UUID getLastConversationId() {
        return lastConversationId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
