package bolsilloApp.api.purchases;

import bolsilloApp.api.purchases.dto.PurchaseResponse;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseIntegrationTest extends AbstractIntegrationTest {

    // T-PUR-01
    @ParameterizedTest
    @CsvSource({
            "100.00, 3",      // classic remainder
            "100.00, 7",      // larger remainder
            "0.03,   3",      // minimal divisible
            "0.01,   3",      // can't split evenly
            "99999.99, 12",   // large amount
            "50.00,  1"       // single installment
    })
    void repartoSumaExacto(String totalStr, int installments) throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        long expectedTotalCents = new BigDecimal(totalStr)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValue();

        var req = TestDataFactory.purchaseRequest(catId, new BigDecimal(totalStr), installments);
        var result = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var purchase = objectMapper.readValue(result.getResponse().getContentAsString(), PurchaseResponse.class);

        // Verify SUM in DB
        Long sum = jdbcTemplate.queryForObject(
                "SELECT SUM(amount_cents) FROM transactions WHERE purchase_id = ? AND deleted_at IS NULL",
                Long.class, purchase.id());
        assertThat(sum).isEqualTo(expectedTotalCents);
    }

    // T-PUR-02
    @Test
    void seCreanExactamenteNCuotasConNumerosCorrectos() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();
        int n = 5;

        var req = TestDataFactory.purchaseRequest(catId, new BigDecimal("500.00"), n);
        var result = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var purchase = objectMapper.readValue(result.getResponse().getContentAsString(), PurchaseResponse.class);

        List<Integer> installmentNumbers = jdbcTemplate.queryForList(
                "SELECT installment_number FROM transactions WHERE purchase_id = ? AND deleted_at IS NULL ORDER BY installment_number",
                Integer.class, purchase.id());

        assertThat(installmentNumbers).hasSize(n);
        assertThat(installmentNumbers).containsExactly(1, 2, 3, 4, 5);
    }

    // T-PUR-03
    @Test
    void fechasDeCuotasSeparadasUnMesInclusoCasosBorde() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // Purchase on Jan 31 with 3 installments
        var req = TestDataFactory.purchaseRequest(catId, new BigDecimal("300.00"), 3,
                LocalDate.of(2026, 1, 31));

        var result = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var purchase = objectMapper.readValue(result.getResponse().getContentAsString(), PurchaseResponse.class);

        List<java.sql.Date> dates = jdbcTemplate.queryForList(
                "SELECT transaction_date FROM transactions WHERE purchase_id = ? AND deleted_at IS NULL ORDER BY installment_number",
                java.sql.Date.class, purchase.id());

        assertThat(dates).hasSize(3);
        assertThat(dates.get(0).toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(dates.get(1).toLocalDate()).isEqualTo(LocalDate.of(2026, 2, 28)); // Feb doesn't have 31
        assertThat(dates.get(2).toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    // T-PUR-04
    @Test
    void installmentsInvalidosDevuelve422() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // installments = 0
        var req0 = TestDataFactory.purchaseRequest(catId, new BigDecimal("100"), 0);
        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req0)))
                .andExpect(status().isUnprocessableEntity());

        // installments = 61
        var req61 = TestDataFactory.purchaseRequest(catId, new BigDecimal("100"), 61);
        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req61)))
                .andExpect(status().isUnprocessableEntity());
    }

    // T-PUR-05 -- Real atomicity test moved to PurchaseAtomicityTest.java
    // Uses @SpyBean on TransactionRepository to simulate failure on 3rd installment save
    // and verifies rollback of both purchase and transaction rows.

    // T-PUR-06
    @Test
    void getPurchasesCalculaBienCamposDerivados() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // Create purchase in the past so some installments are "paid" (date <= today)
        var req = TestDataFactory.purchaseRequest(catId, new BigDecimal("300.00"), 3,
                LocalDate.now().minusMonths(1));

        var createResult = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();
        var created = objectMapper.readValue(createResult.getResponse().getContentAsString(), PurchaseResponse.class);

        // GET purchases
        var listResult = mockMvc.perform(get("/api/purchases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<PurchaseResponse> purchases = objectMapper.readValue(
                listResult.getResponse().getContentAsString(), new TypeReference<>() {});

        assertThat(purchases).hasSize(1);
        var p = purchases.getFirst();
        assertThat(p.totalInstallments()).isEqualTo(3);
        assertThat(p.paidInstallments()).isGreaterThanOrEqualTo(1);
        assertThat(p.remainingAmountCents()).isLessThan(p.totalAmountCents());
    }
}
