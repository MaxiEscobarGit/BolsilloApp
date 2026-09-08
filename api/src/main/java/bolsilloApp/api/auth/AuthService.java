package bolsilloApp.api.auth;

import bolsilloApp.api.auth.dto.AuthResponse;
import bolsilloApp.api.auth.dto.LoginRequest;
import bolsilloApp.api.auth.dto.RegisterRequest;
import bolsilloApp.api.auth.model.RefreshToken;
import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (authRepository.existsByEmail(request.email())) {
            throw new ApiException("Email already registered", "EMAIL_EXISTS", 409);
        }

        String currency = request.defaultCurrency() != null && !request.defaultCurrency().isBlank()
                ? request.defaultCurrency().toUpperCase()
                : "ARS";

        var user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .defaultCurrency(currency)
                .aiMessagesResetAt(LocalDateTime.now())
                .build();
        authRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        var user = authRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("Invalid credentials", "INVALID_CREDENTIALS", 401));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException("Invalid credentials", "INVALID_CREDENTIALS", 401);
        }

        return buildAuthResponse(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        String hash = sha256(rawToken);

        // Check if this token was already revoked (reuse detection)
        var maybeRevoked = refreshTokenRepository.findByTokenHash(hash);
        if (maybeRevoked.isPresent() && maybeRevoked.get().isRevoked()) {
            // Token reuse detected - revoke ALL tokens for this user
            refreshTokenRepository.revokeAllByUserId(maybeRevoked.get().getUser().getId());
            throw new ApiException("Token reuse detected, all sessions revoked", "TOKEN_REUSE", 401);
        }

        var rt = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new ApiException("Invalid refresh token", "INVALID_TOKEN", 401));

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            rt.setRevoked(true);
            throw new ApiException("Refresh token expired", "TOKEN_EXPIRED", 401);
        }

        // Rotation: revoke old, issue new
        rt.setRevoked(true);

        return buildAuthResponse(rt.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        String hash = sha256(rawToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .ifPresent(rt -> rt.setRevoked(true));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail());
        String rawRefresh = UUID.randomUUID().toString();

        var rt = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefresh))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();
        refreshTokenRepository.save(rt);

        return new AuthResponse(accessToken, rawRefresh, user.getId(), user.getEmail(), user.getDisplayName());
    }

    private String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
