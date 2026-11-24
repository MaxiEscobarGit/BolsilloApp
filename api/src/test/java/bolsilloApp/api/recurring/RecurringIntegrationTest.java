package bolsilloApp.api.recurring;

import bolsilloApp.api.recurring.dto.RecurringResponse;
import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecurringIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    RecurringService recurringService;

    @Test
    void laProximaFechaEsLaPrimeraConEseDiaDesdeHoy() {
        assertThat(RecurringService.nextOccurrence(10, LocalDate.of(2026, 11, 23))).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(RecurringService.nextOccurrence(23, LocalDate.of(2026, 11, 23))).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(RecurringService.nextOccurrence(28, LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void sePoneAlDiaConLosMesesSalteadosUnaSolaVez() throws Exception {
        String token = registerAndGetToken();
        RecurringResponse created = read(create(token, "EXPENSE", getSystemCategoryId("Servicios"), "ARS", notToday()).andExpect(status().isCreated()));
        LocalDate due = LocalDate.now().minusMonths(2);
        jdbcTemplate.update("UPDATE recurring SET next_date = ? WHERE id = ?", due, created.id());

        assertThat(recurringService.materializeDue(LocalDate.now())).isEqualTo(3);
        assertThat(recurringService.materializeDue(LocalDate.now())).isZero();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE source = 'RECURRING' AND description = 'Internet'", Integer.class);
        assertThat(count).isEqualTo(3);
        LocalDate next = jdbcTemplate.queryForObject("SELECT next_date FROM recurring WHERE id = ?", LocalDate.class, created.id());
        assertThat(next).isEqualTo(due.plusMonths(3));
    }

    @Test
    void sinCotizacionNoCreaNadaYSeReintentaDespues() throws Exception {
        String token = registerAndGetToken();
        RecurringResponse created = read(create(token, "EXPENSE", getSystemCategoryId("Servicios"), "USD", notToday()).andExpect(status().isCreated()));
        jdbcTemplate.update("UPDATE recurring SET next_date = ? WHERE id = ?", LocalDate.now(), created.id());

        assertThat(recurringService.materializeDue(LocalDate.now())).isZero();
        LocalDate next = jdbcTemplate.queryForObject("SELECT next_date FROM recurring WHERE id = ?", LocalDate.class, created.id());
        assertThat(next).isEqualTo(LocalDate.now());
    }

    @Test
    void validaTipoDiaYDueno() throws Exception {
        String tokenA = registerAndGetToken("a@test.com");
        String tokenB = registerAndGetToken("b@test.com");

        create(tokenA, "EXPENSE", getSystemIncomeCategoryId(), "ARS", 10).andExpect(status().isUnprocessableEntity());
        create(tokenA, "EXPENSE", getSystemExpenseCategoryId(), "ARS", 29).andExpect(status().isBadRequest());

        RecurringResponse created = read(create(tokenA, "INCOME", getSystemIncomeCategoryId(), "ARS", 5).andExpect(status().isCreated()));
        mockMvc.perform(delete("/api/recurring/" + created.id()).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/recurring").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
        mockMvc.perform(delete("/api/recurring/" + created.id()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
    }

    // A day other than today, so creating it doesn't also create today's movement.
    private static int notToday() {
        return LocalDate.now().getDayOfMonth() % 28 + 1;
    }

    private ResultActions create(String token, String type, UUID categoryId, String currency, int day) throws Exception {
        return mockMvc.perform(post("/api/recurring")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"%s","amount":15000,"currency":"%s","description":"Internet","categoryId":"%s","dayOfMonth":%d}
                        """.formatted(type, currency, categoryId, day)));
    }

    private RecurringResponse read(ResultActions result) throws Exception {
        return objectMapper.readValue(result.andReturn().getResponse().getContentAsString(), RecurringResponse.class);
    }
}
