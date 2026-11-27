package bolsilloApp.api.debts.dto;

import bolsilloApp.api.debts.DebtDirection;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDebtRequest(
        @NotBlank @Size(max = 200) String counterpartName,
        @NotNull @Positive @DecimalMax("10000000000") BigDecimal amount,
        @NotNull DebtDirection direction,
        @Size(max = 500) String description,
        LocalDate dueDate,
        @Pattern(regexp = "^([A-Za-z]{3})?$", message = "Must be a 3-letter currency code") String currency
) {}
