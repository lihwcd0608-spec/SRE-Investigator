package com.maiya.investigator.infra;

import com.maiya.investigator.domain.memory.MemoryFact;
import com.maiya.investigator.domain.memory.MemoryFactStore;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryMemoryFactStore implements MemoryFactStore {
    private final ConcurrentHashMap<UUID, MemoryFact> facts = new ConcurrentHashMap<>();

    @Override
    public MemoryFact save(MemoryFact fact) {
        facts.put(fact.id(), fact);
        return fact;
    }

    @Override
    public List<MemoryFact> recentByProject(UUID projectId, int limit) {
        return facts.values().stream()
                .filter(fact -> fact.projectId().equals(projectId))
                .sorted(Comparator.comparing(MemoryFact::updatedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        facts.values().removeIf(fact -> fact.projectId().equals(projectId));
    }
}
