package bolsilloApp.api.goals.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Positive adds to what was saved, negative takes it out.
public record ContributionRequest(@NotNull BigDecimal amount) {}
