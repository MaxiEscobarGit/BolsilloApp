package bolsilloApp.api.metrics.dto;

import java.util.List;
import java.util.Map;

public record MetricsContext(
        Long totalIncomeCents,
        Long totalExpenseCents,
        Long balanceCents,
        Map<String, Long> expenseByCategory,
        Map<String, Long> incomeByCategory,
        List<UpcomingInstallment> upcomingInstallments,
        List<ActiveDebt> activeDebts
) {
    public record UpcomingInstallment(String description, Long amountCents, String dueDate) {}
    public record ActiveDebt(String counterpartName, String direction, Long amountCents) {}
}
