package com.maiya.investigator.infra;

import com.maiya.investigator.domain.feishu.FeishuThread;
import com.maiya.investigator.domain.feishu.FeishuThreadStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryFeishuThreadStore implements FeishuThreadStore {
    private final ConcurrentHashMap<String, FeishuThread> threads = new ConcurrentHashMap<>();

    @Override
    public Optional<FeishuThread> find(String chatId, String senderId) {
        return Optional.ofNullable(threads.get(key(chatId, senderId)));
    }

    @Override
    public FeishuThread upsert(String chatId, String senderId, UUID conversationId, Instant now) {
        return threads.compute(key(chatId, senderId), (ignored, existing) -> existing == null
                ? FeishuThread.create(chatId, senderId, conversationId, now)
                : existing.with(conversationId, now));
    }

    @Override
    public void clear(String chatId, String senderId) {
        threads.remove(key(chatId, senderId));
    }

    private static String key(String chatId, String senderId) {
        return (chatId == null ? "" : chatId) + '\u0000' + (senderId == null ? "" : senderId);
    }
}
