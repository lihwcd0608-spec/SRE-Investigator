package com.maiya.investigator.domain.memory;

import java.util.List;
import java.util.UUID;

public interface MemoryFactStore {
    MemoryFact save(MemoryFact fact);

    List<MemoryFact> recentByProject(UUID projectId, int limit);

    void deleteByProjectId(UUID projectId);
}
