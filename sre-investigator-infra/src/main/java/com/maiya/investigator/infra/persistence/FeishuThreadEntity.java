package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investigator_feishu_thread")
public class FeishuThreadEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "chat_id", nullable = false, length = 128)
    private String chatId;
    @Column(name = "sender_id", nullable = false, length = 128)
    private String senderId;
    @Column(name = "conversation_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID conversationId;
    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    protected FeishuThreadEntity() {
    }

    public FeishuThreadEntity(UUID id, String chatId, String senderId, UUID conversationId, Instant lastActiveAt) {
        this.id = id;
        this.chatId = chatId;
        this.senderId = senderId;
        this.conversationId = conversationId;
        this.lastActiveAt = lastActiveAt;
    }

    public UUID getId() {
        return id;
    }

    public String getChatId() {
        return chatId;
    }

    public String getSenderId() {
        return senderId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
