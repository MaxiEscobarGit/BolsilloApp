package bolsilloApp.api.budgets.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// Monthly cap in pesos.
public record SetBudgetRequest(@NotNull @Positive @DecimalMax("10000000000") BigDecimal amount) {}
