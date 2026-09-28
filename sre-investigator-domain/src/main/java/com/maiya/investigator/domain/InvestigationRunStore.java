package com.maiya.investigator.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestigationRunStore {
    InvestigationRun save(InvestigationRun run);

    Optional<InvestigationRun> findById(UUID id);

    List<InvestigationRun> listByProject(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
