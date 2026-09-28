package com.maiya.investigator.infra;

import com.maiya.investigator.domain.automation.AutomationExecution;
import com.maiya.investigator.domain.automation.AutomationJob;
import com.maiya.investigator.domain.automation.AutomationJobStore;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryAutomationJobStore implements AutomationJobStore {
    private final ConcurrentHashMap<UUID, AutomationJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AutomationExecution> executions = new ConcurrentHashMap<>();

    @Override
    public AutomationJob save(AutomationJob job) {
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public Optional<AutomationJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<AutomationJob> listByProject(UUID projectId) {
        return listByProjects(List.of(projectId));
    }

    @Override
    public List<AutomationJob> listByProjects(List<UUID> projectIds) {
        List<AutomationJob> found = new java.util.ArrayList<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return found;
        }
        for (AutomationJob job : jobs.values()) {
            if (projectIds.contains(job.projectId())) {
                found.add(job);
            }
        }
        found.sort(Comparator.comparing(AutomationJob::updatedAt).reversed());
        return found;
    }

    @Override
    public List<AutomationJob> due(Instant now) {
        return jobs.values().stream()
                .filter(AutomationJob::enabled)
                .filter(job -> job.nextRunAt() != null && !job.nextRunAt().isAfter(now))
                .toList();
    }

    @Override
    public void delete(UUID id) {
        jobs.remove(id);
        executions.values().removeIf(item -> item.jobId().equals(id));
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        List<UUID> ids = jobs.values().stream()
                .filter(job -> job.projectId().equals(projectId))
                .map(AutomationJob::id)
                .toList();
        ids.forEach(this::delete);
    }

    @Override
    public AutomationExecution saveExecution(AutomationExecution execution) {
        executions.put(execution.id(), execution);
        return execution;
    }

    @Override
    public Optional<AutomationExecution> findExecution(UUID id) {
        return Optional.ofNullable(executions.get(id));
    }

    @Override
    public List<AutomationExecution> executions(UUID jobId, int limit) {
        return executions.values().stream()
                .filter(item -> item.jobId().equals(jobId))
                .sorted(Comparator.comparing(AutomationExecution::startedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }
}
