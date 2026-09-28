package com.maiya.investigator.infra;

import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryProjectRepository implements ProjectRepository {
    private final ConcurrentHashMap<UUID, InvestigatorProject> projects = new ConcurrentHashMap<>();

    @Override
    public InvestigatorProject save(InvestigatorProject project) {
        boolean slugTaken = projects.values().stream()
                .anyMatch(item -> item.slug().equals(project.slug()) && !item.id().equals(project.id()));
        if (slugTaken) {
            throw new IllegalArgumentException("项目 slug 已存在：" + project.slug());
        }
        projects.put(project.id(), project);
        return project;
    }

    @Override
    public List<InvestigatorProject> findAll() {
        return List.copyOf(projects.values());
    }

    @Override
    public Optional<InvestigatorProject> findById(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    @Override
    public void delete(UUID id) {
        if (projects.remove(id) == null) {
            throw new IllegalArgumentException("项目不存在：" + id);
        }
    }
}
