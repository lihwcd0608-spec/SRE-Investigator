package com.maiya.investigator.infra.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeishuThreadJpaRepository extends JpaRepository<FeishuThreadEntity, UUID> {
    Optional<FeishuThreadEntity> findByChatIdAndSenderId(String chatId, String senderId);

    void deleteByChatIdAndSenderId(String chatId, String senderId);
}
