package com.maiya.investigator.domain.usage;

import java.time.LocalDate;
import java.util.List;

public record UsageReport(
        LocalDate from,
        LocalDate to,
        List<UsageRow> byUser,
        List<UsageRow> byRole,
        List<UsageRow> byModel,
        UsageTotals totals
) {
}
