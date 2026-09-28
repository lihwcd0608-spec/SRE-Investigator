package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.domain.feishu.FeishuThread;
import com.maiya.investigator.domain.feishu.FeishuThreadStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MysqlFeishuThreadStore implements FeishuThreadStore {
    private final FeishuThreadJpaRepository repository;

    public MysqlFeishuThreadStore(FeishuThreadJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<FeishuThread> find(String chatId, String senderId) {
        return repository.findByChatIdAndSenderId(chatId, senderId).map(MysqlFeishuThreadStore::toDomain);
    }

    @Override
    @Transactional
    public FeishuThread upsert(String chatId, String senderId, UUID conversationId, Instant now) {
        FeishuThreadEntity entity = repository.findByChatIdAndSenderId(chatId, senderId).orElse(null);
        if (entity == null) {
            entity = new FeishuThreadEntity(UUID.randomUUID(), chatId, senderId, conversationId, now);
        } else {
            entity.setConversationId(conversationId);
            entity.setLastActiveAt(now);
        }
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public void clear(String chatId, String senderId) {
        repository.deleteByChatIdAndSenderId(chatId, senderId);
    }

    private static FeishuThread toDomain(FeishuThreadEntity entity) {
        return new FeishuThread(
                entity.getId(),
                entity.getChatId(),
                entity.getSenderId(),
                entity.getConversationId(),
                entity.getLastActiveAt()
        );
    }
}
