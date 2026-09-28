package com.maiya.investigator.infra.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BindingMetricJpaRepository extends JpaRepository<BindingMetricEntity, UUID> {

    List<BindingMetricEntity> findByBindingIdAndMetricKeyOrderByCollectedAtDesc(
            UUID bindingId, String metricKey, Pageable pageable);

    void deleteByCollectedAtBefore(Instant before);
}
