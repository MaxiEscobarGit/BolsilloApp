package bolsilloApp.api.budgets.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// Monthly cap in pesos.
public record SetBudgetRequest(@NotNull @Positive BigDecimal amount) {}
