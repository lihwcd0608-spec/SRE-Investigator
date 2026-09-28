package com.maiya.investigator.domain.feishu;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FeishuThreadStore {
    Optional<FeishuThread> find(String chatId, String senderId);

    FeishuThread upsert(String chatId, String senderId, UUID conversationId, Instant now);

    void clear(String chatId, String senderId);
}
