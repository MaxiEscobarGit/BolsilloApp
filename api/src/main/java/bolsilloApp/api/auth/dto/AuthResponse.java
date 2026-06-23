package bolsilloApp.api.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {}
