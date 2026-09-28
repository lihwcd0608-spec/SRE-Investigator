package com.maiya.investigator.domain.feishu;

import java.time.Instant;
import java.util.UUID;

/**
 * 飞书群「发送人 → 会话」映射。同一 chat 里每个 sender 各自一条会话线程，
 * 用于按人隔离上下文，避免多人同群串台。
 */
public record FeishuThread(
        UUID id,
        String chatId,
        String senderId,
        UUID conversationId,
        Instant lastActiveAt
) {
    public static FeishuThread create(String chatId, String senderId, UUID conversationId, Instant now) {
        return new FeishuThread(UUID.randomUUID(), chatId, senderId, conversationId, now);
    }

    public FeishuThread with(UUID conversationId, Instant now) {
        return new FeishuThread(id, chatId, senderId, conversationId, now);
    }
}
