package bolsilloApp.api.users;

import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.users.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileIntegrationTest extends AbstractIntegrationTest {

    @Test
    void perfilNuevoTieneValoresPorDefectoYCuotaCompleta() throws Exception {
        String token = registerAndGetToken();

        var profile = me(token);

        assertThat(profile.defaultCurrency()).isEqualTo("ARS");
        assertThat(profile.preferredExchangeSource()).isEqualTo("blue");
        assertThat(profile.aiMessagesUsed()).isZero();
        assertThat(profile.aiMessagesLimit()).isEqualTo(100);
        assertThat(profile.aiMessagesResetAt()).isNotNull();
    }

    @Test
    void patchCambiaSoloLosCamposEnviadosYPersiste() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredExchangeSource":"mep"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency":"USD"}
                                """))
                .andExpect(status().isOk());

        var profile = me(token);
        assertThat(profile.preferredExchangeSource()).isEqualTo("mep");
        assertThat(profile.defaultCurrency()).isEqualTo("USD");
    }

    @Test
    void valoresInvalidosDevuelven400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredExchangeSource":"cripto"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency":"EUR"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    private UserProfileResponse me(String token) throws Exception {
        var result = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), UserProfileResponse.class);
    }
}
