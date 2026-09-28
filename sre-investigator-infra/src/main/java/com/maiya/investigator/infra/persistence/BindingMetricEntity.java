package com.maiya.investigator.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "investigator_binding_metric",
        indexes = @Index(name = "idx_binding_metric_time", columnList = "binding_id, metric_key, collected_at")
)
public class BindingMetricEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "binding_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID bindingId;

    @Column(name = "metric_key", nullable = false, length = 64)
    private String metricKey;

    @Column(name = "metric_value", length = 128)
    private String metricValue;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected BindingMetricEntity() {
    }

    public BindingMetricEntity(UUID id, UUID bindingId, String metricKey, String metricValue, Instant collectedAt) {
        this.id = id;
        this.bindingId = bindingId;
        this.metricKey = metricKey;
        this.metricValue = metricValue;
        this.collectedAt = collectedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBindingId() {
        return bindingId;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public String getMetricValue() {
        return metricValue;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }
}
