package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.InvestigationRun;
import com.maiya.investigator.domain.InvestigationRunStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MysqlInvestigationRunStore implements InvestigationRunStore {
    private final InvestigationRunJpaRepository runs;

    public MysqlInvestigationRunStore(InvestigationRunJpaRepository runs) {
        this.runs = runs;
    }

    @Override
    public InvestigationRun save(InvestigationRun run) {
        return toDomain(runs.save(new InvestigationRunEntity(
                run.id(),
                run.projectId(),
                run.environment().code(),
                run.mode() == null ? "INVESTIGATE" : run.mode().name(),
                run.question(),
                run.status().name(),
                run.report(),
                run.createdAt(),
                run.updatedAt()
        )));
    }

    @Override
    public Optional<InvestigationRun> findById(UUID id) {
        return runs.findById(id).map(MysqlInvestigationRunStore::toDomain);
    }

    @Override
    public List<InvestigationRun> listByProject(UUID projectId) {
        return runs.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(MysqlInvestigationRunStore::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByProjectId(UUID projectId) {
        runs.deleteByProjectId(projectId);
    }

    private static InvestigationRun toDomain(InvestigationRunEntity entity) {
        return new InvestigationRun(
                entity.getId(),
                entity.getProjectId(),
                Environment.from(entity.getEnvironment()),
                ConversationMode.from(entity.getConversationMode()),
                entity.getQuestion(),
                InvestigationRun.RunStatus.valueOf(entity.getStatus()),
                entity.getReport(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
