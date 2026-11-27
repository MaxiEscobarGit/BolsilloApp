package bolsilloApp.api.goals.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// Positive adds to what was saved, negative takes it out.
public record ContributionRequest(@NotNull @DecimalMin("-10000000000") @DecimalMax("10000000000") BigDecimal amount) {}
