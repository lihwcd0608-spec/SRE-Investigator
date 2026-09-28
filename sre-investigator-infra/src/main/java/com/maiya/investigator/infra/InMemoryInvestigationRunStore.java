package com.maiya.investigator.infra;

import com.maiya.investigator.domain.InvestigationRun;
import com.maiya.investigator.domain.InvestigationRunStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryInvestigationRunStore implements InvestigationRunStore {
    private final ConcurrentHashMap<UUID, InvestigationRun> runs = new ConcurrentHashMap<>();

    @Override
    public InvestigationRun save(InvestigationRun run) {
        runs.put(run.id(), run);
        return run;
    }

    @Override
    public Optional<InvestigationRun> findById(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public List<InvestigationRun> listByProject(UUID projectId) {
        return runs.values().stream()
                .filter(run -> run.projectId().equals(projectId))
                .sorted(Comparator.comparing(InvestigationRun::createdAt).reversed())
                .toList();
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        runs.values().removeIf(run -> run.projectId().equals(projectId));
    }
}
