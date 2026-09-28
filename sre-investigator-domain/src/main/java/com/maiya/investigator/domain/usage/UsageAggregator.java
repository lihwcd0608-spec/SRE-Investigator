package com.maiya.investigator.domain.usage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UsageAggregator {
    private UsageAggregator() {
    }

    public static UsageReport aggregate(List<ModelUsageEvent> events, LocalDate from, LocalDate to) {
        List<ModelUsageEvent> safe = events == null ? List.of() : events;
        Map<String, MutableRow> byUser = new LinkedHashMap<>();
        Map<String, MutableRow> byRole = new LinkedHashMap<>();
        Map<String, MutableRow> byModel = new LinkedHashMap<>();
        MutableRow totals = new MutableRow(null, null, null, null, null);
        for (ModelUsageEvent event : safe) {
            totals.add(event);
            String provider = event.provider();
            String model = event.model();
            byUser.computeIfAbsent(
                    key(event.userId(), provider, model),
                    ignored -> new MutableRow(event.userId(), event.username(), null, provider, model)
            ).add(event);
            byModel.computeIfAbsent(
                    key(provider, model),
                    ignored -> new MutableRow(null, null, null, provider, model)
            ).add(event);
            for (String role : event.roleCodeList()) {
                byRole.computeIfAbsent(
                        key(role, provider, model),
                        ignored -> new MutableRow(null, null, role, provider, model)
                ).add(event);
            }
        }
        return new UsageReport(
                from,
                to,
                sorted(byUser),
                sorted(byRole),
                sorted(byModel),
                totals.toTotals()
        );
    }

    private static List<UsageRow> sorted(Map<String, MutableRow> rows) {
        List<UsageRow> result = new ArrayList<>(rows.size());
        rows.values().forEach(row -> result.add(row.toRow()));
        result.sort(Comparator
                .comparingLong(UsageRow::callCount).reversed()
                .thenComparing(row -> nullToEmpty(row.username()))
                .thenComparing(row -> nullToEmpty(row.roleCode()))
                .thenComparing(row -> nullToEmpty(row.provider()))
                .thenComparing(row -> nullToEmpty(row.model())));
        return List.copyOf(result);
    }

    private static String key(Object... parts) {
        StringBuilder builder = new StringBuilder();
        for (Object part : parts) {
            if (!builder.isEmpty()) {
                builder.append('\u0001');
            }
            builder.append(part == null ? "" : part);
        }
        return builder.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class MutableRow {
        private final UUID userId;
        private final String username;
        private final String roleCode;
        private final String provider;
        private final String model;
        private long callCount;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private boolean tokenIncomplete;

        private MutableRow(UUID userId, String username, String roleCode, String provider, String model) {
            this.userId = userId;
            this.username = username;
            this.roleCode = roleCode;
            this.provider = provider;
            this.model = model;
        }

        private void add(ModelUsageEvent event) {
            callCount += event.callCount();
            promptTokens += zeroIfNull(event.promptTokens());
            completionTokens += zeroIfNull(event.completionTokens());
            totalTokens += zeroIfNull(event.totalTokens());
            if (event.promptTokens() == null || event.completionTokens() == null || event.totalTokens() == null) {
                tokenIncomplete = true;
            }
        }

        private UsageRow toRow() {
            return new UsageRow(
                    userId == null ? null : userId.toString(),
                    username,
                    roleCode,
                    provider,
                    model,
                    callCount,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    tokenIncomplete
            );
        }

        private UsageTotals toTotals() {
            return new UsageTotals(callCount, promptTokens, completionTokens, totalTokens, tokenIncomplete);
        }

        private static long zeroIfNull(Long value) {
            return value == null ? 0L : value;
        }
    }
}
