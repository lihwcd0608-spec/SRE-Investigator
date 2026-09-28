package com.maiya.investigator.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeishuChatBindingJpaRepository extends JpaRepository<FeishuChatBindingEntity, UUID> {
    Optional<FeishuChatBindingEntity> findByChatId(String chatId);

    List<FeishuChatBindingEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
