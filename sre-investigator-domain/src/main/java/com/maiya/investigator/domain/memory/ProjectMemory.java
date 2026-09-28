package com.maiya.investigator.domain.memory;

import java.util.List;
import java.util.UUID;

public interface ProjectMemory {
    List<String> recall(UUID projectId, String query, int maxChars);

    static ProjectMemory noop() {
        return (projectId, query, maxChars) -> List.of();
    }
}
