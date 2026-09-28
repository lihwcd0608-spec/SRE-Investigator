package com.maiya.investigator.domain.usage;

import java.time.Instant;
import java.util.List;

public interface ModelUsageStore {
    ModelUsageEvent save(ModelUsageEvent event);

    List<ModelUsageEvent> listBetween(Instant fromInclusive, Instant toExclusive);
}
