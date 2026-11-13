package bolsilloApp.api.auth;

import bolsilloApp.api.auth.model.AuthAttempt.Kind;
import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuthThrottleServiceTest extends AbstractIntegrationTest {

    private static final String KEY = AuthThrottleService.loginKey("a@b.com", "1.1.1.1");

    @Autowired
    private AuthThrottleService service;

    private void insertAttempt(Kind kind, String key, LocalDateTime at) {
        jdbcTemplate.update("INSERT INTO auth_attempts (kind, throttle_key, attempted_at) VALUES (?, ?, ?)",
                kind.name(), key, Timestamp.valueOf(at));
    }

    private int rows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM auth_attempts", Integer.class);
    }

    @Test
    void elConteoSaleDeLaTablaNoDeMemoria() {
        // Filas insertadas por "otra instancia": el service nunca las vio y aun asi bloquea
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            insertAttempt(Kind.LOGIN, KEY, now.minusMinutes(5 - i));
        }
        // intento mas viejo de los 5 en t-5min, ventana 15min -> ~10 minutos de bloqueo
        assertThat(service.blockedForSeconds(Kind.LOGIN, KEY))
                .hasValueSatisfying(s -> assertThat(s).isBetween(9 * 60L, 10 * 60L));
    }

    @Test
    void intentosFueraDeLaVentanaNoCuentan() {
        LocalDateTime old = LocalDateTime.now().minusMinutes(16);
        for (int i = 0; i < 5; i++) {
            insertAttempt(Kind.LOGIN, KEY, old);
        }
        assertThat(service.blockedForSeconds(Kind.LOGIN, KEY)).isEmpty();
    }

    @Test
    void recordPersisteClearBorraYClavesSonIndependientes() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.blockedForSeconds(Kind.LOGIN, KEY)).isEmpty();
            service.record(Kind.LOGIN, KEY);
        }
        assertThat(rows()).isEqualTo(5);
        assertThat(service.blockedForSeconds(Kind.LOGIN, KEY)).isPresent();
        assertThat(service.blockedForSeconds(Kind.LOGIN, AuthThrottleService.loginKey(" A@B.com ", "1.1.1.1"))).isPresent();
        assertThat(service.blockedForSeconds(Kind.LOGIN, AuthThrottleService.loginKey("a@b.com", "2.2.2.2"))).isEmpty();
        assertThat(service.blockedForSeconds(Kind.REGISTER, "1.1.1.1")).isEmpty();

        service.clear(Kind.LOGIN, KEY);
        assertThat(service.blockedForSeconds(Kind.LOGIN, KEY)).isEmpty();
        assertThat(rows()).isZero();
    }

    @Test
    void recordBorraLasFilasQueSalieronDeTodaVentana() {
        insertAttempt(Kind.REGISTER, "9.9.9.9", LocalDateTime.now().minusHours(2));
        service.record(Kind.LOGIN, KEY);
        assertThat(rows()).isEqualTo(1);
    }
}
