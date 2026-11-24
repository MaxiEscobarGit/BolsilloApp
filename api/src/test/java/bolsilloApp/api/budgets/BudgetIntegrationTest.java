package bolsilloApp.api.budgets;

import bolsilloApp.api.budgets.dto.BudgetResponse;
import bolsilloApp.api.categories.dto.CategoryResponse;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import bolsilloApp.api.transactions.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BudgetIntegrationTest extends AbstractIntegrationTest {

    @Test
    void presupuestoMuestraLoGastadoEsteMesEnEsaCategoria() throws Exception {
        String token = registerAndGetToken();
        UUID comida = getSystemExpenseCategoryId();
        setBudget(token, comida, "50000").andExpect(status().isOk());

        expense(token, comida, "12000", LocalDate.now());
        expense(token, comida, "3000", LocalDate.now());
        expense(token, comida, "99999", LocalDate.now().minusMonths(1)); // last month: not counted
        expense(token, getSystemCategoryId("Transporte"), "500", LocalDate.now()); // another category

        List<BudgetResponse> budgets = list(token);
        assertThat(budgets).hasSize(1);
        assertThat(budgets.getFirst().amountCents()).isEqualTo(5_000_000L);
        assertThat(budgets.getFirst().spentCents()).isEqualTo(1_500_000L);
        assertThat(budgets.getFirst().categoryName()).isEqualTo("Comida");
    }

    @Test
    void ponerloDeNuevoReemplazaElMonto() throws Exception {
        String token = registerAndGetToken();
        UUID comida = getSystemExpenseCategoryId();
        setBudget(token, comida, "100").andExpect(status().isOk());
        setBudget(token, comida, "250.50").andExpect(status().isOk());

        assertThat(list(token)).singleElement().extracting(BudgetResponse::amountCents).isEqualTo(25_050L);
    }

    @Test
    void categoriaDeIngresoDevuelve422YMontoInvalido400() throws Exception {
        String token = registerAndGetToken();
        setBudget(token, getSystemIncomeCategoryId(), "100").andExpect(status().isUnprocessableEntity());
        setBudget(token, getSystemExpenseCategoryId(), "0").andExpect(status().isBadRequest());
    }

    @Test
    void categoriaDeOtroUsuarioDevuelve404YLosPresupuestosNoSeMezclan() throws Exception {
        String tokenA = registerAndGetToken("a@test.com");
        String tokenB = registerAndGetToken("b@test.com");
        UUID privadaA = createCategory(tokenA, "Privada");

        setBudget(tokenB, privadaA, "100").andExpect(status().isNotFound());
        setBudget(tokenA, privadaA, "100").andExpect(status().isOk());
        assertThat(list(tokenB)).isEmpty();
    }

    @Test
    void borrarPresupuestoOLaCategoriaLoQuita() throws Exception {
        String token = registerAndGetToken();
        UUID comida = getSystemExpenseCategoryId();
        UUID propia = createCategory(token, "Mascota");
        setBudget(token, comida, "100").andExpect(status().isOk());
        setBudget(token, propia, "100").andExpect(status().isOk());

        mockMvc.perform(delete("/api/budgets/" + comida).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/categories/" + propia).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(list(token)).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions setBudget(String token, UUID categoryId, String amount) throws Exception {
        return mockMvc.perform(put("/api/budgets/" + categoryId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    private List<BudgetResponse> list(String token) throws Exception {
        var result = mockMvc.perform(get("/api/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<BudgetResponse>>() {});
    }

    private void expense(String token, UUID categoryId, String amount, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.transactionRequest(categoryId, new BigDecimal(amount), TransactionType.EXPENSE, date))))
                .andExpect(status().isCreated());
    }

    private UUID createCategory(String token, String name) throws Exception {
        var result = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CategoryResponse.class).id();
    }
}
