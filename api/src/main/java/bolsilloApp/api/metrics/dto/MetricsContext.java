package bolsilloApp.api.metrics.dto;

import java.util.List;
import java.util.Map;

public record MetricsContext(
        Long totalIncomeCents,
        Long totalExpenseCents,
        Long balanceCents,
        Map<String, Long> expenseByCategory,
        List<UpcomingInstallment> upcomingInstallments
) {
    public record UpcomingInstallment(String description, Long amountCents, String dueDate) {}
}
