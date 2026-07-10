package bolsilloApp.api.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(UUID userId, String email) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public String extractEmail(String token) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public UUID extractUserId(String token) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isTokenExpired(String token) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
