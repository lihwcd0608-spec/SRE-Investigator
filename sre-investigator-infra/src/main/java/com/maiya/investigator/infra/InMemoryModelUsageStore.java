package com.maiya.investigator.infra;

import com.maiya.investigator.domain.usage.ModelUsageEvent;
import com.maiya.investigator.domain.usage.ModelUsageStore;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryModelUsageStore implements ModelUsageStore {
    private final CopyOnWriteArrayList<ModelUsageEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public ModelUsageEvent save(ModelUsageEvent event) {
        events.add(event);
        return event;
    }

    @Override
    public List<ModelUsageEvent> listBetween(Instant fromInclusive, Instant toExclusive) {
        return events.stream()
                .filter(event -> !event.occurredAt().isBefore(fromInclusive) && event.occurredAt().isBefore(toExclusive))
                .sorted(Comparator.comparing(ModelUsageEvent::occurredAt))
                .toList();
    }
}
