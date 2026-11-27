package bolsilloApp.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// password max 72: bcrypt ignora lo que pasa de 72 bytes; con tope se evita aceptar passwords "truncadas" en silencio.
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName,
        @Pattern(regexp = "^([A-Za-z]{3})?$", message = "Must be a 3-letter currency code") String defaultCurrency
) {}
