package bolsilloApp.api.goals.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// Target in pesos; the deadline is optional.
public record GoalRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive @DecimalMax("10000000000") BigDecimal target,
        LocalDate deadline
) {}
