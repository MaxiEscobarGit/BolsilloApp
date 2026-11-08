package bolsilloApp.api.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(decodeAndValidate(jwtSecret));
        this.expirationMs = expirationMs;
    }

    static final int MIN_SECRET_BITS = 256;

    // Falla el arranque si el secret falta, no es Base64 o tiene menos de 256 bits (HS256).
    static byte[] decodeAndValidate(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET no esta configurado");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(jwtSecret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET debe estar en Base64 (generalo con: openssl rand -base64 32)");
        }
        if (bytes.length * 8 < MIN_SECRET_BITS) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + MIN_SECRET_BITS
                    + " bits; tiene " + (bytes.length * 8));
        }
        return bytes;
    }

    public String generateToken(UUID userId, String email) {
        var now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractEmail(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).get("userId", String.class));
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
