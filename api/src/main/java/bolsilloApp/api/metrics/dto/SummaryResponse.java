package bolsilloApp.api.metrics.dto;

import java.time.LocalDate;
import java.util.List;

public record SummaryResponse(
        LocalDate from,
        LocalDate to,
        Long totalExpenseCents,
        Long totalIncomeCents,
        Long balanceCents,
        List<CategoryTotal> byCategory,
        List<DailyTotal> dailyTotals,
        PeriodComparison vsPreviousPeriod
) {
    public record CategoryTotal(String categoryName, String type, Long totalCents) {}
    public record DailyTotal(LocalDate date, Long expenseCents, Long incomeCents) {}
    public record PeriodComparison(Long previousExpenseCents, Long previousIncomeCents,
                                    Double expenseChangePercent, Double incomeChangePercent) {}
}
