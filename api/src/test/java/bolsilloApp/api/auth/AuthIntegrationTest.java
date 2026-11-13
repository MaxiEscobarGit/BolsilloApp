package bolsilloApp.api.auth;

import bolsilloApp.api.auth.dto.AuthResponse;
import bolsilloApp.api.auth.dto.LoginRequest;
import bolsilloApp.api.auth.dto.RegisterRequest;
import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    // T-AUTH-01
    @Test
    void registroExitosoDevuelve201ConCamposCompletos() throws Exception {
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RegisterRequest("test@test.com", "password123", "Test User", null))))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.userId()).isNotNull();
        assertThat(resp.email()).isEqualTo("test@test.com");
        assertThat(resp.displayName()).isEqualTo("Test User");
    }

    // T-AUTH-02
    @Test
    void registroNoExponePaswwordHash() throws Exception {
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RegisterRequest("test@test.com", "password123", "Test User", null))))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("passwordHash");
        assertThat(json).doesNotContain("password_hash");
        assertThat(json).doesNotContain("password123");
    }

    // T-AUTH-03
    @Test
    void contrasenaSePersisteHasheada() throws Exception {
        registerUser("test@test.com", "password123", "Test User");

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'test@test.com'", String.class);
        assertThat(hash).isNotEqualTo("password123");
        assertThat(hash).startsWith("$2");
    }

    // T-AUTH-04
    @Test
    void emailDuplicadoDevuelve409() throws Exception {
        registerUser("dup@test.com", "password123", "User 1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RegisterRequest("dup@test.com", "password123", "User 2", null))))
                .andExpect(status().isConflict());
    }

    // T-AUTH-05
    @Test
    void contrasenaCortaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RegisterRequest("test@test.com", "short", "Test User", null))))
                .andExpect(status().isBadRequest());
    }

    // T-AUTH-06
    @Test
    void emailInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RegisterRequest("not-an-email", "password123", "Test User", null))))
                .andExpect(status().isBadRequest());
    }

    // T-AUTH-07
    @Test
    void loginExitosoDevuelve200ConTokens() throws Exception {
        registerUser("test@test.com", "password123", "Test User");

        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest("test@test.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
    }

    // T-AUTH-08
    @Test
    void loginConContrasenaIncorrectaDevuelve401() throws Exception {
        registerUser("test@test.com", "password123", "Test User");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest("test@test.com", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    // T-AUTH-09
    @Test
    @SuppressWarnings("unchecked")
    void loginConEmailInexistenteDevuelveMismoMensajeQueContrasenaIncorrecta() throws Exception {
        registerUser("test@test.com", "password123", "Test User");

        var wrongPw = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest("test@test.com", "wrongpassword"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        var noUser = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest("noexiste@test.com", "password123"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> map1 = objectMapper.readValue(wrongPw.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> map2 = objectMapper.readValue(noUser.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {});

        assertThat(map1.get("error")).isEqualTo(map2.get("error"));
        assertThat(map1.get("code")).isEqualTo(map2.get("code"));
        assertThat(map1.get("status")).isEqualTo(map2.get("status"));
    }

    // T-AUTH-10
    @Test
    void refreshConTokenValidoDevuelveTokensNuevos() throws Exception {
        var auth = registerDefaultUser();

        var result = mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", auth.refreshToken()))
                .andExpect(status().isOk())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.accessToken()).isNotBlank();
        // ponytail: access tokens CAN be identical within same second (deterministic JWT). Refresh tokens are always unique (UUID).
        assertThat(resp.refreshToken()).isNotBlank().isNotEqualTo(auth.refreshToken());
    }

    // T-AUTH-11
    @Test
    void refreshTokenUsadoQuedaRevocado() throws Exception {
        var auth = registerDefaultUser();

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", auth.refreshToken()))
                .andExpect(status().isOk());

        int revokedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id " +
                        "WHERE u.email = 'test@test.com' AND rt.revoked = true",
                Integer.class);
        assertThat(revokedCount).isGreaterThanOrEqualTo(1);
    }

    // T-AUTH-12
    @Test
    void reutilizarRefreshTokenRevocadoRevocaTodosLosTokens() throws Exception {
        var auth = registerDefaultUser();
        String firstRefresh = auth.refreshToken();

        // Use the token (rotates: old revoked, new issued)
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", firstRefresh))
                .andExpect(status().isOk());

        int activeBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id " +
                        "WHERE u.email = 'test@test.com' AND rt.revoked = false",
                Integer.class);
        assertThat(activeBefore).isGreaterThanOrEqualTo(1);

        // Reuse the old (revoked) token — should trigger revoke-all
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Refresh-Token", firstRefresh))
                .andExpect(status().isUnauthorized());

        int activeAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id " +
                        "WHERE u.email = 'test@test.com' AND rt.revoked = false",
                Integer.class);
        assertThat(activeAfter).isEqualTo(0);
    }

    // T-AUTH-13
    @Test
    void logoutDevuelve204YRevocaToken() throws Exception {
        var auth = registerDefaultUser();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Refresh-Token", auth.refreshToken()))
                .andExpect(status().isNoContent());

        int active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id " +
                        "WHERE u.email = 'test@test.com' AND rt.revoked = false",
                Integer.class);
        assertThat(active).isEqualTo(0);
    }

    // T-AUTH-14
    @Test
    void requestSinAuthorizationDevuelve401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    // T-AUTH-15
    @Test
    void requestConTokenMalformadoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer abc123"))
                .andExpect(status().isUnauthorized());
    }

    // T-AUTH-16
    @Test
    void requestConTokenFirmadoConOtroSecretDevuelve401() throws Exception {
        String fakeJwt = "eyJhbGciOiJIUzI1NiJ9" +
                ".eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwidXNlcklkIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIiwiZXhwIjo5OTk5OTk5OTk5fQ" +
                ".XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + fakeJwt))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions registerFrom(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new RegisterRequest(email, "password123", "User", null))));
    }

    @Test
    void elRegistroSeLimitaPorIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            registerFrom("10.0.0.9", "u" + i + "@test.com").andExpect(status().isCreated());
        }

        registerFrom("10.0.0.9", "u5@test.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REGISTRATIONS"))
                .andExpect(jsonPath("$.status").value(429));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(5);

        // Otra IP no esta afectada
        registerFrom("10.0.0.10", "u5@test.com").andExpect(status().isCreated());
    }
}
