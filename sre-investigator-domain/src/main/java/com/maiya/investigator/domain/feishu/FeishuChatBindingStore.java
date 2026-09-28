package com.maiya.investigator.domain.feishu;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeishuChatBindingStore {
    FeishuChatBinding save(FeishuChatBinding binding);

    Optional<FeishuChatBinding> findById(UUID id);

    Optional<FeishuChatBinding> findByChatId(String chatId);

    List<FeishuChatBinding> listByProject(UUID projectId);

    void delete(UUID id);

    void deleteByProjectId(UUID projectId);
}
