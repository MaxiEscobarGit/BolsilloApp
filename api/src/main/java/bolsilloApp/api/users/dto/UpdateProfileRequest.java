package bolsilloApp.api.users.dto;

import jakarta.validation.constraints.Pattern;

// Every field is optional: only the ones sent change. Sources match exchange-rates.sources.
public record UpdateProfileRequest(
        @Pattern(regexp = "ARS|USD", message = "Must be ARS or USD") String defaultCurrency,
        @Pattern(regexp = "oficial|blue|mep|tarjeta", message = "Must be oficial, blue, mep or tarjeta") String preferredExchangeSource
) {}
