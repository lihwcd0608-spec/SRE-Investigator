package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.feishu.FeishuChatBinding;
import com.maiya.investigator.domain.feishu.FeishuChatBindingStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MysqlFeishuChatBindingStore implements FeishuChatBindingStore {
    private final FeishuChatBindingJpaRepository repository;

    public MysqlFeishuChatBindingStore(FeishuChatBindingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public FeishuChatBinding save(FeishuChatBinding binding) {
        return toDomain(repository.save(toEntity(binding)));
    }

    @Override
    public Optional<FeishuChatBinding> findById(UUID id) {
        return repository.findById(id).map(MysqlFeishuChatBindingStore::toDomain);
    }

    @Override
    public Optional<FeishuChatBinding> findByChatId(String chatId) {
        return repository.findByChatId(chatId).map(MysqlFeishuChatBindingStore::toDomain);
    }

    @Override
    public List<FeishuChatBinding> listByProject(UUID projectId) {
        List<FeishuChatBinding> found = new ArrayList<>();
        for (FeishuChatBindingEntity entity : repository.findByProjectIdOrderByUpdatedAtDesc(projectId)) {
            found.add(toDomain(entity));
        }
        return found;
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        repository.deleteByProjectId(projectId);
    }

    private static FeishuChatBindingEntity toEntity(FeishuChatBinding binding) {
        return new FeishuChatBindingEntity(
                binding.id(),
                binding.projectId(),
                binding.chatId(),
                binding.chatName(),
                binding.environment().name().toLowerCase(),
                FeishuChatBinding.joinAllowed(binding.allowedEnvironments()),
                binding.defaultRoute(),
                binding.defaultRepositoryId(),
                binding.lastConversationId(),
                binding.enabled(),
                binding.createdAt(),
                binding.updatedAt()
        );
    }

    private static FeishuChatBinding toDomain(FeishuChatBindingEntity entity) {
        return new FeishuChatBinding(
                entity.getId(),
                entity.getProjectId(),
                entity.getChatId(),
                entity.getChatName(),
                Environment.from(entity.getEnvironment()),
                FeishuChatBinding.splitAllowed(entity.getAllowedEnvironments(), Environment.from(entity.getEnvironment())),
                entity.getDefaultRoute(),
                entity.getDefaultRepositoryId(),
                entity.getLastConversationId(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
