package bolsilloApp.api.metrics.dto;

import java.time.YearMonth;

public record SummaryResponse(
        YearMonth period,
        Long totalIncomeCents,
        Long totalExpenseCents,
        Long balanceCents,
        Long totalDebtOwedCents,
        Long totalDebtOweCents
) {}
