package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.ProjectRepository;
import com.maiya.investigator.infra.ProjectEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MysqlProjectRepository implements ProjectRepository {
    private final ProjectJpaRepository projects;

    public MysqlProjectRepository(ProjectJpaRepository projects) {
        this.projects = projects;
    }

    @Override
    public InvestigatorProject save(InvestigatorProject project) {
        if (projects.existsBySlugAndIdNot(project.slug(), project.id())) {
            throw new IllegalArgumentException("项目 slug 已存在：" + project.slug());
        }
        return toDomain(projects.save(toEntity(project)));
    }

    @Override
    public List<InvestigatorProject> findAll() {
        return projects.findAll().stream().map(MysqlProjectRepository::toDomain).toList();
    }

    @Override
    public Optional<InvestigatorProject> findById(UUID id) {
        return projects.findById(id).map(MysqlProjectRepository::toDomain);
    }

    @Override
    public void delete(UUID id) {
        if (!projects.existsById(id)) {
            throw new IllegalArgumentException("项目不存在：" + id);
        }
        projects.deleteById(id);
    }

    private static ProjectEntity toEntity(InvestigatorProject project) {
        return new ProjectEntity(
                project.id(),
                project.slug(),
                project.name(),
                project.topology(),
                project.createdAt()
        );
    }

    private static InvestigatorProject toDomain(ProjectEntity entity) {
        return new InvestigatorProject(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getTopology(),
                entity.getCreatedAt()
        );
    }
}
