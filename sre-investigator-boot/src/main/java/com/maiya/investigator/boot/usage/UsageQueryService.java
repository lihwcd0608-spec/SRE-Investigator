package com.maiya.investigator.boot.usage;

import com.maiya.investigator.domain.usage.ModelUsageStore;
import com.maiya.investigator.domain.usage.UsageAggregator;
import com.maiya.investigator.domain.usage.UsageReport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class UsageQueryService {
    private final ModelUsageStore store;

    public UsageQueryService(ModelUsageStore store) {
        this.store = store;
    }

    public UsageReport summarize(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();
        return UsageAggregator.aggregate(store.listBetween(fromInstant, toInstant), start, end);
    }
}
