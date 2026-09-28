package com.maiya.investigator.boot.automation;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);
    private final AutomationJobService jobs;

    public AutomationScheduler(AutomationJobService jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
    public void tick() {
        try {
            jobs.runDue(Instant.now());
        } catch (RuntimeException exception) {
            log.warn("定时扫描失败：{}", exception.getMessage());
        }
    }
}
