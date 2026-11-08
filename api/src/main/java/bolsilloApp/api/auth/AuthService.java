package bolsilloApp.api.auth;

import bolsilloApp.api.auth.dto.AuthResponse;
import bolsilloApp.api.auth.dto.LoginRequest;
import bolsilloApp.api.auth.dto.RegisterRequest;
import bolsilloApp.api.auth.model.RefreshToken;
import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.shared.exception.TooManyRequestsException;
import bolsilloApp.api.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private volatile String dummyPasswordHash;

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

    public AuthResponse login(LoginRequest request, String clientIp) {
        loginAttemptService.blockedForSeconds(request.email(), clientIp).ifPresent(seconds -> {
            throw new TooManyRequestsException(
                    "Too many failed login attempts, try again later", "TOO_MANY_LOGIN_ATTEMPTS", seconds);
        });

        var user = authRepository.findByEmail(request.email()).orElse(null);
        // Siempre correr bcrypt (con hash dummy si el usuario no existe) para no filtrar
        // por timing si el email esta registrado.
        String hash = user != null ? user.getPasswordHash() : dummyPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !matches) {
            loginAttemptService.recordFailure(request.email(), clientIp);
            throw new ApiException("Invalid credentials", "INVALID_CREDENTIALS", 401);
        }

        loginAttemptService.recordSuccess(request.email(), clientIp);
        return buildAuthResponse(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        var rt = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new ApiException("Invalid refresh token", "INVALID_TOKEN", 401));
        var user = rt.getUser();

        if (rt.isRevoked()) {
            // Reuse detection: un token ya rotado/revocado se volvio a presentar -> revocar TODAS las sesiones
            revokeAllAndFail(user.getId());
        }

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            // No se marca como revocado: reintentar con un token vencido no debe disparar reuse detection
            throw new ApiException("Refresh token expired", "TOKEN_EXPIRED", 401);
        }

        // Rotacion atomica: si dos requests concurrentes usan el mismo token, solo una gana;
        // la otra se trata como reuso.
        if (refreshTokenRepository.revokeIfActive(rt.getId()) == 0) {
            revokeAllAndFail(user.getId());
        }

        return buildAuthResponse(user);
    }

    private void revokeAllAndFail(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        throw new ApiException("Token reuse detected, all sessions revoked", "TOKEN_REUSE", 401);
    }

    @Transactional
    public void logout(String rawToken) {
        String hash = sha256(rawToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .ifPresent(rt -> rt.setRevoked(true));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail());
        String rawRefresh = newRefreshToken();

        var rt = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefresh))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();
        refreshTokenRepository.save(rt);

        return new AuthResponse(accessToken, rawRefresh, user.getId(), user.getEmail(), user.getDisplayName());
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String dummyPasswordHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode(newRefreshToken());
            dummyPasswordHash = hash;
        }
        return hash;
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
