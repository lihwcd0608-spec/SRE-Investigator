package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.domain.usage.ModelUsageEvent;
import com.maiya.investigator.domain.usage.ModelUsageStore;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MysqlModelUsageStore implements ModelUsageStore {
    private final ModelUsageJpaRepository usages;

    public MysqlModelUsageStore(ModelUsageJpaRepository usages) {
        this.usages = usages;
    }

    @Override
    public ModelUsageEvent save(ModelUsageEvent event) {
        return toDomain(usages.save(toEntity(event)));
    }

    @Override
    public List<ModelUsageEvent> listBetween(Instant fromInclusive, Instant toExclusive) {
        return usages.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        fromInclusive,
                        toExclusive
                )
                .stream()
                .map(MysqlModelUsageStore::toDomain)
                .toList();
    }

    private static ModelUsageEntity toEntity(ModelUsageEvent event) {
        return new ModelUsageEntity(
                event.id(),
                event.occurredAt(),
                event.userId(),
                event.username(),
                event.roleCodes(),
                event.projectId(),
                event.provider(),
                event.model(),
                event.callCount(),
                event.promptTokens(),
                event.completionTokens(),
                event.totalTokens()
        );
    }

    private static ModelUsageEvent toDomain(ModelUsageEntity entity) {
        return new ModelUsageEvent(
                entity.getId(),
                entity.getOccurredAt(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getRoleCodes(),
                entity.getProjectId(),
                entity.getProvider(),
                entity.getModel(),
                entity.getCallCount(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getTotalTokens()
        );
    }
}
