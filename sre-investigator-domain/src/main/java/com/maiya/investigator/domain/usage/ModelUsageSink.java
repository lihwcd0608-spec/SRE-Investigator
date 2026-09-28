package com.maiya.investigator.domain.usage;

@FunctionalInterface
public interface ModelUsageSink {
    void accept(ModelUsageEvent event);
}
