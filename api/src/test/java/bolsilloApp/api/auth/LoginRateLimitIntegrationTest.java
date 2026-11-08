package bolsilloApp.api.auth;

import bolsilloApp.api.auth.dto.LoginRequest;
import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginRateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "victima@test.com";
    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() throws Exception {
        registerUser(EMAIL, PASSWORD, "Victima");
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(req -> { req.setRemoteAddr(ip); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new LoginRequest(email, password))));
    }

    private void failTimes(int n, String email, String ip) throws Exception {
        for (int i = 0; i < n; i++) {
            login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void trasCincoFallosElLoginQuedaBloqueadoCon429() throws Exception {
        failTimes(5, EMAIL, "10.0.0.1");

        // Incluso con la contrasena correcta
        login(EMAIL, PASSWORD, "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void cuatroFallosNoBloquean() throws Exception {
        failTimes(4, EMAIL, "10.0.0.1");
        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isOk());
    }

    @Test
    void loginExitosoReseteaElContador() throws Exception {
        failTimes(4, EMAIL, "10.0.0.1");
        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isOk());
        failTimes(4, EMAIL, "10.0.0.1");
        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isOk());
    }

    @Test
    void elBloqueoEsPorEmailMasIp() throws Exception {
        failTimes(5, EMAIL, "10.0.0.1");

        // Otra IP, mismo email: no bloqueado
        login(EMAIL, PASSWORD, "10.0.0.2").andExpect(status().isOk());
    }

    @Test
    void elBloqueoIgnoraMayusculasDelEmail() throws Exception {
        failTimes(5, "VICTIMA@test.com", "10.0.0.1");
        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void emailsInexistentesTambienCuentan() throws Exception {
        failTimes(5, "noexiste@test.com", "10.0.0.1");
        login("noexiste@test.com", "x", "10.0.0.1").andExpect(status().isTooManyRequests());
    }
}
