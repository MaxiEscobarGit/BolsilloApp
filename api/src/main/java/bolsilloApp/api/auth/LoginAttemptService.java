package bolsilloApp.api.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteccion contra fuerza bruta en login, por email + IP, en memoria.
 * Ventana deslizante: con {@code maxAttempts} fallos dentro de {@code window} la clave queda
 * bloqueada hasta que el fallo mas viejo salga de la ventana. Un login exitoso resetea la clave.
 * Estado por instancia (no compartido entre replicas); suficiente para el MVP en una sola instancia.
 */
@Service
public class LoginAttemptService {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.window:15m}") Duration window) {
        this(maxAttempts, window, Clock.systemUTC());
    }

    LoginAttemptService(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Segundos restantes de bloqueo, o vacio si la clave no esta bloqueada. */
    public Optional<Long> blockedForSeconds(String email, String ip) {
        Deque<Instant> deque = failures.get(key(email, ip));
        if (deque == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        synchronized (deque) {
            prune(deque, now);
            if (deque.size() < maxAttempts) {
                return Optional.empty();
            }
            Instant unlockAt = deque.peekFirst().plus(window);
            return Optional.of(Math.max(1, Duration.between(now, unlockAt).toSeconds()));
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (failures.size() > SWEEP_THRESHOLD) {
            sweep(now);
        }
        Deque<Instant> deque = failures.computeIfAbsent(key(email, ip), k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, now);
            deque.addLast(now);
            // No hace falta guardar mas que maxAttempts timestamps
            while (deque.size() > maxAttempts) {
                deque.pollFirst();
            }
        }
    }

    public void recordSuccess(String email, String ip) {
        failures.remove(key(email, ip));
    }

    /** Solo para tests. */
    public void reset() {
        failures.clear();
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
            deque.pollFirst();
        }
    }

    private void sweep(Instant now) {
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static String key(String email, String ip) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + (ip == null ? "unknown" : ip);
    }
}
