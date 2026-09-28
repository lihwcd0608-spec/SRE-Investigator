package com.maiya.investigator.infra;

import com.maiya.investigator.domain.feishu.FeishuChatBinding;
import com.maiya.investigator.domain.feishu.FeishuChatBindingStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryFeishuChatBindingStore implements FeishuChatBindingStore {
    private final ConcurrentHashMap<UUID, FeishuChatBinding> bindings = new ConcurrentHashMap<>();

    @Override
    public FeishuChatBinding save(FeishuChatBinding binding) {
        bindings.put(binding.id(), binding);
        return binding;
    }

    @Override
    public Optional<FeishuChatBinding> findById(UUID id) {
        return Optional.ofNullable(bindings.get(id));
    }

    @Override
    public Optional<FeishuChatBinding> findByChatId(String chatId) {
        return bindings.values().stream()
                .filter(item -> item.chatId().equals(chatId))
                .findFirst();
    }

    @Override
    public List<FeishuChatBinding> listByProject(UUID projectId) {
        List<FeishuChatBinding> found = new ArrayList<>();
        for (FeishuChatBinding binding : bindings.values()) {
            if (binding.projectId().equals(projectId)) {
                found.add(binding);
            }
        }
        found.sort(Comparator.comparing(FeishuChatBinding::updatedAt).reversed());
        return found;
    }

    @Override
    public void delete(UUID id) {
        bindings.remove(id);
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        bindings.values().removeIf(item -> item.projectId().equals(projectId));
    }
}
