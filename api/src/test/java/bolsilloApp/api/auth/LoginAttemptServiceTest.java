package bolsilloApp.api.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(5, Duration.ofMinutes(15), clock);

    @Test
    void bloqueaTrasNFallosYDesbloqueaAlSalirDeLaVentana() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.blockedForSeconds("a@b.com", "1.1.1.1")).isEmpty();
            service.recordFailure("a@b.com", "1.1.1.1");
            clock.advance(Duration.ofMinutes(1));
        }
        // primer fallo en t=0, ahora t=5min -> bloqueado ~10 minutos mas
        assertThat(service.blockedForSeconds("a@b.com", "1.1.1.1"))
                .hasValueSatisfying(s -> assertThat(s).isEqualTo(Duration.ofMinutes(10).toSeconds()));

        clock.advance(Duration.ofMinutes(10));
        assertThat(service.blockedForSeconds("a@b.com", "1.1.1.1")).isEmpty();
    }

    @Test
    void fallosEspaciadosFueraDeLaVentanaNoBloquean() {
        for (int i = 0; i < 10; i++) {
            service.recordFailure("a@b.com", "1.1.1.1");
            clock.advance(Duration.ofMinutes(4));
        }
        // nunca hay 5 fallos dentro de 15 minutos
        assertThat(service.blockedForSeconds("a@b.com", "1.1.1.1")).isEmpty();
    }

    @Test
    void exitoReseteaYClavesSonIndependientes() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("a@b.com", "1.1.1.1");
        }
        assertThat(service.blockedForSeconds("a@b.com", "2.2.2.2")).isEmpty();
        assertThat(service.blockedForSeconds("otro@b.com", "1.1.1.1")).isEmpty();
        assertThat(service.blockedForSeconds(" A@B.com ", "1.1.1.1")).isPresent();

        service.recordSuccess("a@b.com", "1.1.1.1");
        assertThat(service.blockedForSeconds("a@b.com", "1.1.1.1")).isEmpty();
    }
}
