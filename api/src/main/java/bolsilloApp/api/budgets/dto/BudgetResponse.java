package bolsilloApp.api.budgets.dto;

import java.util.UUID;

// spentCents: this month's expenses in the category, converted to ARS like every other total.
public record BudgetResponse(
        UUID categoryId,
        String categoryName,
        String emoji,
        String colorHex,
        long amountCents,
        long spentCents
) {}
