package bolsilloApp.api.shared.security;

import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void preflightDesdeOrigenPermitidoDevuelveHeadersCors() throws Exception {
        mockMvc.perform(options("/api/transactions")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }

    @Test
    void preflightDeRefreshPermiteHeaderRefreshToken() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Refresh-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Refresh-Token")));
    }

    @Test
    void preflightDesdeOrigenNoPermitidoEsRechazado() throws Exception {
        mockMvc.perform(options("/api/transactions")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightConHeaderNoPermitidoEsRechazado() throws Exception {
        mockMvc.perform(options("/api/transactions")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Evil"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestSimpleDesdeOrigenPermitidoIncluyeAllowOrigin() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(get("/api/transactions")
                        .header("Origin", "http://localhost:3000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
