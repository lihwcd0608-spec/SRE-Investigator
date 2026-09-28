package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.domain.memory.MemoryFact;
import com.maiya.investigator.domain.memory.MemoryFactStore;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MysqlMemoryFactStore implements MemoryFactStore {
    private final MemoryFactJpaRepository facts;

    public MysqlMemoryFactStore(MemoryFactJpaRepository facts) {
        this.facts = facts;
    }

    @Override
    public MemoryFact save(MemoryFact fact) {
        return toDomain(facts.save(new MemoryFactEntity(
                fact.id(),
                fact.projectId(),
                fact.kind(),
                fact.content(),
                fact.sourceConversationId(),
                fact.qdrantPointId(),
                fact.createdAt(),
                fact.updatedAt()
        )));
    }

    @Override
    public List<MemoryFact> recentByProject(UUID projectId, int limit) {
        return facts.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .limit(Math.max(1, limit))
                .map(MysqlMemoryFactStore::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByProjectId(UUID projectId) {
        facts.deleteByProjectId(projectId);
    }

    private static MemoryFact toDomain(MemoryFactEntity entity) {
        return new MemoryFact(
                entity.getId(),
                entity.getProjectId(),
                entity.getKind(),
                entity.getContent(),
                entity.getSourceConversationId(),
                entity.getQdrantPointId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
