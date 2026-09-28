package com.maiya.investigator.infra.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelUsageJpaRepository extends JpaRepository<ModelUsageEntity, UUID> {
    List<ModelUsageEntity> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            Instant fromInclusive,
            Instant toExclusive
    );
}
