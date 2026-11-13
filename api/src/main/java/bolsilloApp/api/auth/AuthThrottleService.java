package bolsilloApp.api.auth;

import bolsilloApp.api.auth.model.AuthAttempt;
import bolsilloApp.api.auth.model.AuthAttempt.Kind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Throttling de auth persistido en {@code auth_attempts} (compartido entre instancias y sobrevive reinicios).
 * Ventana deslizante por clave: con {@code maxAttempts} intentos dentro de {@code window} la clave queda
 * bloqueada hasta que el intento mas viejo de esos N salga de la ventana.
 * LOGIN: clave email normalizado + "|" + ip, cuenta fallos, un login exitoso la limpia.
 * REGISTER: clave ip, cuenta registros exitosos.
 */
@Service
public class AuthThrottleService {

    private record Limit(int maxAttempts, Duration window) {}

    private final AuthAttemptRepository repository;
    private final Map<Kind, Limit> limits;
    private final Duration longestWindow;

    public AuthThrottleService(
            AuthAttemptRepository repository,
            @Value("${app.security.login.max-attempts:5}") int loginMaxAttempts,
            @Value("${app.security.login.window:15m}") Duration loginWindow,
            @Value("${app.security.register.max-attempts:5}") int registerMaxAttempts,
            @Value("${app.security.register.window:1h}") Duration registerWindow) {
        this.repository = repository;
        this.limits = Map.of(
                Kind.LOGIN, new Limit(loginMaxAttempts, loginWindow),
                Kind.REGISTER, new Limit(registerMaxAttempts, registerWindow));
        this.longestWindow = loginWindow.compareTo(registerWindow) > 0 ? loginWindow : registerWindow;
    }

    public static String loginKey(String email, String ip) {
        return email.trim().toLowerCase(Locale.ROOT) + "|" + ip;
    }

    /** Segundos restantes de bloqueo, o vacio si la clave no esta bloqueada. */
    public Optional<Long> blockedForSeconds(Kind kind, String key) {
        Limit limit = limits.get(kind);
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> recent = repository.findRecent(
                kind, key, now.minus(limit.window()), PageRequest.of(0, limit.maxAttempts()));
        if (recent.size() < limit.maxAttempts()) {
            return Optional.empty();
        }
        LocalDateTime unlockAt = recent.get(recent.size() - 1).plus(limit.window());
        return Optional.of(Math.max(1, Duration.between(now, unlockAt).toSeconds()));
    }

    @Transactional
    public void record(Kind kind, String key) {
        LocalDateTime now = LocalDateTime.now();
        repository.save(AuthAttempt.builder().kind(kind).throttleKey(key).attemptedAt(now).build());
        // Limpieza oportunista: lo que ya salio de toda ventana no se vuelve a consultar
        repository.deleteOlderThan(now.minus(longestWindow));
    }

    @Transactional
    public void clear(Kind kind, String key) {
        repository.deleteByKindAndKey(kind, key);
    }
}
