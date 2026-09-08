package bolsilloApp.api.debts;

import bolsilloApp.api.debts.dto.DebtResponse;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DebtIntegrationTest extends AbstractIntegrationTest {

    // T-DEBT-01
    @Test
    void crearDeudaDevuelve201ConStatusPending() throws Exception {
        String token = registerAndGetToken();

        var result = mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.oweRequest(new BigDecimal("100.00")))))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), DebtResponse.class);
        assertThat(resp.status()).isEqualTo(DebtStatus.PENDING);
        assertThat(resp.direction()).isEqualTo(DebtDirection.OWE);
        assertThat(resp.amountCents()).isEqualTo(10000L);
    }

    // T-DEBT-02
    @Test
    void directionInvalidoDevuelve400() throws Exception {
        String token = registerAndGetToken();

        String body = """
                {"counterpartName":"Juan","amount":100,"direction":"INVALID","description":"test"}
                """;
        mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // T-DEBT-03
    @Test
    void marcarComoPagadaSetStatusPaidYPaidAt() throws Exception {
        String token = registerAndGetToken();

        var createResult = mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.oweRequest(new BigDecimal("50.00")))))
                .andExpect(status().isCreated())
                .andReturn();
        var created = objectMapper.readValue(createResult.getResponse().getContentAsString(), DebtResponse.class);

        var payResult = mockMvc.perform(patch("/api/debts/" + created.id() + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var paid = objectMapper.readValue(payResult.getResponse().getContentAsString(), DebtResponse.class);

        assertThat(paid.status()).isEqualTo(DebtStatus.PAID);
        assertThat(paid.paidAt()).isNotNull();
    }

    // T-DEBT-04
    @Test
    void marcarComoPagadaDeudaYaPagadaDevuelve409() throws Exception {
        String token = registerAndGetToken();

        var createResult = mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.oweRequest(new BigDecimal("50.00")))))
                .andExpect(status().isCreated())
                .andReturn();
        var created = objectMapper.readValue(createResult.getResponse().getContentAsString(), DebtResponse.class);

        // First pay succeeds
        mockMvc.perform(patch("/api/debts/" + created.id() + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Second pay fails
        mockMvc.perform(patch("/api/debts/" + created.id() + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // T-DEBT-05
    @Test
    void listadoFiltraPorStatusYDirection() throws Exception {
        String token = registerAndGetToken();

        // Create OWE debt (PENDING)
        mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.oweRequest(new BigDecimal("100")))))
                .andExpect(status().isCreated());

        // Create OWED debt (PENDING)
        mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.owedRequest(new BigDecimal("200")))))
                .andExpect(status().isCreated());

        // Filter by direction=OWE
        var oweResult = mockMvc.perform(get("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .param("direction", "OWE"))
                .andExpect(status().isOk())
                .andReturn();
        List<DebtResponse> oweDebts = objectMapper.readValue(
                oweResult.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(oweDebts).allMatch(d -> d.direction() == DebtDirection.OWE);
        assertThat(oweDebts).hasSize(1);

        // Filter by status=PENDING
        var pendingResult = mockMvc.perform(get("/api/debts")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn();
        List<DebtResponse> pendingDebts = objectMapper.readValue(
                pendingResult.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(pendingDebts).allMatch(d -> d.status() == DebtStatus.PENDING);
        assertThat(pendingDebts).hasSize(2);
    }
}
