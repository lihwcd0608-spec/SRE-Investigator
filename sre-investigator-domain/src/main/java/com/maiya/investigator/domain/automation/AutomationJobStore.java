package com.maiya.investigator.domain.automation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationJobStore {
    AutomationJob save(AutomationJob job);

    Optional<AutomationJob> findById(UUID id);

    List<AutomationJob> listByProject(UUID projectId);

    List<AutomationJob> listByProjects(List<UUID> projectIds);

    List<AutomationJob> due(Instant now);

    void delete(UUID id);

    void deleteByProjectId(UUID projectId);

    AutomationExecution saveExecution(AutomationExecution execution);

    Optional<AutomationExecution> findExecution(UUID id);

    List<AutomationExecution> executions(UUID jobId, int limit);
}
