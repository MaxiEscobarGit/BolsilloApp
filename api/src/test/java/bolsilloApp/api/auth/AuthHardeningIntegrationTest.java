package bolsilloApp.api.auth;

import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthHardeningIntegrationTest extends AbstractIntegrationTest {

    private int activeTokens(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id " +
                        "WHERE u.email = ? AND rt.revoked = false", Integer.class, email);
    }

    // Antes: el token vencido se marcaba revocado y el reintento disparaba reuse detection,
    // cerrando TODAS las sesiones del usuario.
    @Test
    void reintentarRefreshVencidoNoRevocaLasOtrasSesiones() throws Exception {
        var session1 = registerDefaultUser();
        var session2 = registerUser("otro@test.com", "password123", "Otro"); // control de otro usuario
        // segunda sesion del mismo usuario
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        assertThat(activeTokens("test@test.com")).isEqualTo(2);

        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 day' WHERE token_hash = encode(sha256(?::bytea), 'hex')",
                session1.refreshToken());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/refresh").header("Refresh-Token", session1.refreshToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }

        // La otra sesion sigue viva
        assertThat(activeTokens("test@test.com")).isEqualTo(2);
        assertThat(session2.refreshToken()).isNotBlank();
    }

    // Antes: dos refresh concurrentes con el mismo token podian rotar ambos (dos cadenas validas).
    @Test
    void refreshConcurrenteConElMismoTokenSoloRotaUnaVez() throws Exception {
        var auth = registerDefaultUser();
        int threads = 8;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/auth/refresh").header("Refresh-Token", auth.refreshToken()))
                            .andReturn().getResponse().getStatus();
                });
            }
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            int ok = 0;
            for (var f : futures) {
                if (f.get() == 200) ok++;
            }
            assertThat(ok).isLessThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        // Si hubo carrera, la reuse detection deja todo revocado; nunca puede haber 2+ tokens activos
        assertThat(activeTokens("test@test.com")).isLessThanOrEqualTo(1);
    }

    @Test
    void refreshTokenTieneAltaEntropia() throws Exception {
        var auth = registerDefaultUser();
        // 32 bytes en base64url sin padding = 43 caracteres
        assertThat(auth.refreshToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void refreshSinHeaderDevuelve400ConFormatoEstandar() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rutaInexistenteAutenticadaDevuelve404SinDetalles() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(get("/api/no-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void metodoNoSoportadoDevuelve405() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
