package bolsilloApp.api.security;

import bolsilloApp.api.categories.dto.CategoryResponse;
import bolsilloApp.api.debts.dto.DebtResponse;
import bolsilloApp.api.purchases.dto.PurchaseResponse;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import bolsilloApp.api.transactions.dto.UpdateTransactionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIsolationIntegrationTest extends AbstractIntegrationTest {

    private String tokenA;
    private String tokenB;
    private UUID txIdA;       // transaction belonging to user A
    private UUID catIdA;      // custom category belonging to user A
    private UUID debtIdA;     // debt belonging to user A
    private UUID purchaseIdA; // purchase belonging to user A

    @BeforeEach
    void setUpTwoUsers() throws Exception {
        tokenA = registerAndGetToken("userA@test.com");
        tokenB = registerAndGetToken("userB@test.com");

        UUID sysCatId = getSystemExpenseCategoryId();

        // User A creates a custom category
        var catResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("CatA"))))
                .andExpect(status().isCreated())
                .andReturn();
        catIdA = objectMapper.readValue(catResult.getResponse().getContentAsString(), CategoryResponse.class).id();

        // User A creates a transaction
        var txResult = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(sysCatId, "100.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        txIdA = objectMapper.readValue(txResult.getResponse().getContentAsString(), TransactionResponse.class).id();

        // User A creates a debt
        var debtResult = mockMvc.perform(post("/api/debts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.oweRequest(new BigDecimal("50.00")))))
                .andExpect(status().isCreated())
                .andReturn();
        debtIdA = objectMapper.readValue(debtResult.getResponse().getContentAsString(), DebtResponse.class).id();

        // User A creates a purchase
        var purResult = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.purchaseRequest(sysCatId, new BigDecimal("300"), 3))))
                .andExpect(status().isCreated())
                .andReturn();
        purchaseIdA = objectMapper.readValue(purResult.getResponse().getContentAsString(), PurchaseResponse.class).id();
    }

    // T-SEC-01
    @Test
    void getTransactionDeOtroUsuarioDevuelve404() throws Exception {
        mockMvc.perform(get("/api/transactions/" + txIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // T-SEC-02
    @Test
    void patchTransactionDeOtroUsuarioDevuelve404YNoModifica() throws Exception {
        mockMvc.perform(patch("/api/transactions/" + txIdA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UpdateTransactionRequest(
                                null, null, "HACKED", null, null, null))))
                .andExpect(status().isNotFound());

        // Verify not modified
        String desc = jdbcTemplate.queryForObject(
                "SELECT description FROM transactions WHERE id = ?", String.class, txIdA);
        assertThat(desc).isNotEqualTo("HACKED");
    }

    // T-SEC-03
    @Test
    void deleteTransactionDeOtroUsuarioDevuelve404YNoSoftDeletes() throws Exception {
        mockMvc.perform(delete("/api/transactions/" + txIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        Boolean hasDeletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM transactions WHERE id = ?", Boolean.class, txIdA);
        assertThat(hasDeletedAt).isFalse();
    }

    // T-SEC-04
    @Test
    void listadoDeTransaccionesDeBNoIncluyeLasDeA() throws Exception {
        var result = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();

        List<TransactionResponse> txns = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(txns).noneMatch(t -> t.id().equals(txIdA));
    }

    // T-SEC-05
    @Test
    void listadoDeCategoriasDeBNoIncluyelasCustomDeA() throws Exception {
        var result = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();

        List<CategoryResponse> cats = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(cats).noneMatch(c -> c.id().equals(catIdA));
    }

    // T-SEC-06
    @Test
    void deleteCategoryDeOtroUsuarioDevuelve404() throws Exception {
        mockMvc.perform(delete("/api/categories/" + catIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // T-SEC-07
    @Test
    void deudasDeANoAparecenNiSeModificanDesdeB() throws Exception {
        // Can't see A's debt
        mockMvc.perform(get("/api/debts/" + debtIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Can't pay A's debt
        mockMvc.perform(patch("/api/debts/" + debtIdA + "/pay")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // List doesn't include A's debts
        var result = mockMvc.perform(get("/api/debts")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        List<DebtResponse> debts = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(debts).noneMatch(d -> d.id().equals(debtIdA));
    }

    // T-SEC-08
    @Test
    void comprasDeANoAparecenNiSeModificanDesdeB() throws Exception {
        mockMvc.perform(get("/api/purchases/" + purchaseIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        var result = mockMvc.perform(get("/api/purchases")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        List<PurchaseResponse> purchases = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(purchases).noneMatch(p -> p.id().equals(purchaseIdA));
    }

    // T-SEC-09
    @Test
    void metricasDeBNoIncluyenDatosDeA() throws Exception {
        // User A has transactions (created in setup), user B has none
        var result = mockMvc.perform(get("/api/metrics/summary")
                        .header("Authorization", "Bearer " + tokenB)
                        .param("period", "month"))
                .andExpect(status().isOk())
                .andReturn();

        var summary = objectMapper.readValue(result.getResponse().getContentAsString(),
                bolsilloApp.api.metrics.dto.SummaryResponse.class);
        assertThat(summary.totalExpenseCents()).isEqualTo(0L);
        assertThat(summary.totalIncomeCents()).isEqualTo(0L);
    }

    // T-SEC-10
    @Test
    void mandarUserIdDeOtroUsuarioEnBodyNoAfectaAsignacion() throws Exception {
        // Get user A's UUID
        UUID userAId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'userA@test.com'", UUID.class);

        // User B creates a transaction but sends user A's ID in the body (extra field, should be ignored)
        UUID catId = getSystemExpenseCategoryId();
        String body = String.format("""
                {"amount":10,"type":"EXPENSE","description":"spoofed","categoryId":"%s","transactionDate":"%s","userId":"%s"}
                """, catId, java.time.LocalDate.now(), userAId);

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);

        // Verify in DB that the transaction belongs to user B, not A
        UUID actualUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM transactions WHERE id = ?", UUID.class, resp.id());
        assertThat(actualUserId).isNotEqualTo(userAId);
    }
}
