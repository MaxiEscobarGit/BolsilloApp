package bolsilloApp.api.goals;

import bolsilloApp.api.goals.dto.GoalResponse;
import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SavingsGoalIntegrationTest extends AbstractIntegrationTest {

    @Test
    void cuotaMensualCuentaElMesActualYRedondeaParaArriba() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        assertThat(SavingsGoalService.monthly(100_00, LocalDate.of(2026, 11, 30), today)).isEqualTo(100_00);
        assertThat(SavingsGoalService.monthly(100_00, LocalDate.of(2027, 1, 15), today)).isEqualTo(33_34);
        assertThat(SavingsGoalService.monthly(100_00, null, today)).isNull();
        assertThat(SavingsGoalService.monthly(0, LocalDate.of(2027, 1, 15), today)).isNull();
    }

    @Test
    void crearAportarRetirarYEditar() throws Exception {
        String token = registerAndGetToken();
        GoalResponse goal = read(send(post("/api/goals"), token, """
                {"name":"Vacaciones","target":800000,"deadline":"%s"}
                """.formatted(LocalDate.now().plusMonths(3))).andExpect(status().isCreated()));
        assertThat(goal.savedCents()).isZero();
        assertThat(goal.monthlyCents()).isNotNull();

        GoalResponse after = read(send(post("/api/goals/" + goal.id() + "/contributions"), token, "{\"amount\":150000}").andExpect(status().isOk()));
        assertThat(after.savedCents()).isEqualTo(15_000_000L);
        after = read(send(post("/api/goals/" + goal.id() + "/contributions"), token, "{\"amount\":-50000}").andExpect(status().isOk()));
        assertThat(after.savedCents()).isEqualTo(10_000_000L);

        send(post("/api/goals/" + goal.id() + "/contributions"), token, "{\"amount\":-200000}").andExpect(status().isUnprocessableEntity());
        send(post("/api/goals/" + goal.id() + "/contributions"), token, "{\"amount\":0}").andExpect(status().isBadRequest());

        GoalResponse edited = read(send(put("/api/goals/" + goal.id()), token, "{\"name\":\"Viaje\",\"target\":100000}").andExpect(status().isOk()));
        assertThat(edited.name()).isEqualTo("Viaje");
        assertThat(edited.savedCents()).isEqualTo(10_000_000L);
        assertThat(edited.monthlyCents()).isNull(); // reached
    }

    @Test
    void lasMetasSonDeCadaUsuario() throws Exception {
        String tokenA = registerAndGetToken("a@test.com");
        String tokenB = registerAndGetToken("b@test.com");
        GoalResponse goal = read(send(post("/api/goals"), tokenA, "{\"name\":\"Auto\",\"target\":1000}").andExpect(status().isCreated()));

        send(post("/api/goals/" + goal.id() + "/contributions"), tokenB, "{\"amount\":10}").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/goals/" + goal.id()).header("Authorization", "Bearer " + tokenB)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/goals/" + goal.id()).header("Authorization", "Bearer " + tokenA)).andExpect(status().isNoContent());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        return mockMvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private GoalResponse read(ResultActions result) throws Exception {
        return objectMapper.readValue(result.andReturn().getResponse().getContentAsString(), GoalResponse.class);
    }
}
