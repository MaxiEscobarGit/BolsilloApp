package bolsilloApp.api.transactions;

import bolsilloApp.api.categories.dto.CategoryResponse;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import bolsilloApp.api.transactions.dto.TransactionListResponse;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import bolsilloApp.api.transactions.dto.UpdateTransactionRequest;
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

class TransactionIntegrationTest extends AbstractIntegrationTest {

    // T-TRX-01
    @Test
    void crearConAmountPersisteCentavosExactos() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(catId, "1234.56"))))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);

        // Verify in DB, not response
        Long dbCents = jdbcTemplate.queryForObject(
                "SELECT amount_cents FROM transactions WHERE id = ?", Long.class, resp.id());
        assertThat(dbCents).isEqualTo(123456L);
    }

    // T-TRX-02
    @ParameterizedTest
    @CsvSource({
            "0.01, 1",
            "0.99, 99",
            "1.005, 101",       // HALF_UP
            "999999.99, 99999999"
    })
    void conversionDecimalACentavos(String amount, long expectedCents) throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(catId, amount))))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
        Long dbCents = jdbcTemplate.queryForObject(
                "SELECT amount_cents FROM transactions WHERE id = ?", Long.class, resp.id());
        assertThat(dbCents).isEqualTo(expectedCents);
    }

    // T-TRX-03
    @Test
    void transaccionEnArsGuardaMismoCentsYRate1() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var req = new CreateTransactionRequest(
                new BigDecimal("50.00"), TransactionType.EXPENSE, "ARS test",
                catId, LocalDate.now(), "ARS", null);

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
        assertThat(resp.amountArsCents()).isEqualTo(resp.amountCents());
        assertThat(resp.exchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
    }

    // T-TRX-04
    @Test
    void categoryIdInexistenteDevuelve422() throws Exception {
        String token = registerAndGetToken();
        UUID fakeCatId = UUID.randomUUID();

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(fakeCatId, "10.00"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // T-TRX-05
    @Test
    void categoryIdDeOtroUsuarioDevuelve422() throws Exception {
        String tokenA = registerAndGetToken("a@test.com");
        String tokenB = registerAndGetToken("b@test.com");

        // User A creates a custom category
        var catResult = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("PrivadaA"))))
                .andExpect(status().isCreated())
                .andReturn();
        var catA = objectMapper.readValue(catResult.getResponse().getContentAsString(), CategoryResponse.class);

        // User B tries to use A's category
        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(catA.id(), "10.00"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // T-TRX-06
    @Test
    void fechaFuturaMasDeUnDiaDevuelve422() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var req = TestDataFactory.transactionRequest(catId, new BigDecimal("10"),
                TransactionType.EXPENSE, LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    // T-TRX-07
    @Test
    void sourceSeFuerzaAManual() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // Send source=CHAT in body
        String body = String.format("""
                {"amount":10,"type":"EXPENSE","description":"test","categoryId":"%s","transactionDate":"%s","source":"CHAT"}
                """, catId, LocalDate.now());

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
        assertThat(resp.source()).isEqualTo(TransactionSource.MANUAL);
    }

    // T-TRX-08
    @Test
    void listadoFiltraPorRangoDeFechas() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // Create transactions: one inside range, one outside
        var insideReq = TestDataFactory.transactionRequest(catId, new BigDecimal("10"),
                TransactionType.EXPENSE, LocalDate.of(2026, 6, 15));
        var outsideReq = TestDataFactory.transactionRequest(catId, new BigDecimal("20"),
                TransactionType.EXPENSE, LocalDate.of(2026, 3, 1));

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(insideReq)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(outsideReq)))
                .andExpect(status().isCreated());

        var result = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andReturn();

        var listResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), TransactionListResponse.class);
        assertThat(listResponse.data()).hasSize(1);
        assertThat(listResponse.total()).isEqualTo(1);
        assertThat(listResponse.data().getFirst().amountCents()).isEqualTo(1000L);
    }

    // T-TRX-09
    @Test
    void listadoFiltraPorCategoryIdYType() throws Exception {
        String token = registerAndGetToken();
        UUID expenseCat = getSystemExpenseCategoryId();
        UUID incomeCat = getSystemIncomeCategoryId();

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(expenseCat, "10.00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.incomeRequest(incomeCat, new BigDecimal("20.00")))))
                .andExpect(status().isCreated());

        // Filter by categoryId
        var byCat = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", expenseCat.toString()))
                .andExpect(status().isOk())
                .andReturn();
        var catListResponse = objectMapper.readValue(
                byCat.getResponse().getContentAsString(), TransactionListResponse.class);
        assertThat(catListResponse.data()).allMatch(t -> t.categoryId().equals(expenseCat));

        // Filter by type
        var byType = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "INCOME"))
                .andExpect(status().isOk())
                .andReturn();
        var typeListResponse = objectMapper.readValue(
                byType.getResponse().getContentAsString(), TransactionListResponse.class);
        assertThat(typeListResponse.data()).allMatch(t -> t.type() == TransactionType.INCOME);
    }

    // T-TRX-10
    @Test
    void rangoMayorAUnAnioDevuelve422() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2024-01-01")
                        .param("to", "2025-06-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    // T-TRX-11
    @Test
    void limitMayorA200SeAcotaA200() throws Exception {
        String token = registerAndGetToken();

        // Request limit=300, should silently cap to 200 (no error, just capped)
        var result = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "300"))
                .andExpect(status().isOk())
                .andReturn();

        // Verify the limit was capped to 200
        var listResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), TransactionListResponse.class);
        assertThat(listResponse.data().size()).isLessThanOrEqualTo(200);
        assertThat(listResponse.limit()).isEqualTo(200);
    }

    // T-TRX-12
    @Test
    void patchConUnSoloCampoDejaLosDemasIntactos() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var createResult = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(catId, "50.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        var original = objectMapper.readValue(createResult.getResponse().getContentAsString(), TransactionResponse.class);

        // Patch only description
        var patchResult = mockMvc.perform(patch("/api/transactions/" + original.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UpdateTransactionRequest(
                                null, null, "Updated desc", null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        var patched = objectMapper.readValue(patchResult.getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(patched.description()).isEqualTo("Updated desc");
        assertThat(patched.amountCents()).isEqualTo(original.amountCents());
        assertThat(patched.type()).isEqualTo(original.type());
        assertThat(patched.categoryId()).isEqualTo(original.categoryId());
        assertThat(patched.transactionDate()).isEqualTo(original.transactionDate());
    }

    // T-TRX-13 — skipped: user_alias creation on PATCH category is not implemented in current code
    // The requirement mentions PATCH of category_id creates/updates user_alias,
    // but this logic lives in ChatService (AI-driven corrections), not in manual PATCH.

    // T-TRX-14
    @Test
    void patchDeAmountRecalculaAmountArsCents() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var createResult = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(catId, "50.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        var original = objectMapper.readValue(createResult.getResponse().getContentAsString(), TransactionResponse.class);

        // Patch amount to 75.00
        var patchResult = mockMvc.perform(patch("/api/transactions/" + original.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UpdateTransactionRequest(
                                new BigDecimal("75.00"), null, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        var patched = objectMapper.readValue(patchResult.getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(patched.amountCents()).isEqualTo(7500L);
        assertThat(patched.amountArsCents()).isEqualTo(7500L);
    }

    // T-TRX-15
    @Test
    void deleteSetDeletedAtYNoborraLaFila() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var createResult = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(catId, "10.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        var resp = objectMapper.readValue(createResult.getResponse().getContentAsString(), TransactionResponse.class);

        mockMvc.perform(delete("/api/transactions/" + resp.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Row still exists in DB
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, resp.id());
        assertThat(count).isEqualTo(1);

        // deleted_at is set
        Boolean hasDeletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM transactions WHERE id = ?", Boolean.class, resp.id());
        assertThat(hasDeletedAt).isTrue();
    }

    // T-TRX-16
    @Test
    void transaccionConDeletedAtNoApareceEnListado() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var createResult = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.expenseRequest(catId, "10.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        var resp = objectMapper.readValue(createResult.getResponse().getContentAsString(), TransactionResponse.class);

        // Soft delete
        mockMvc.perform(delete("/api/transactions/" + resp.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // List should be empty
        var listResult = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        var listResponse = objectMapper.readValue(
                listResult.getResponse().getContentAsString(), TransactionListResponse.class);
        assertThat(listResponse.data()).noneMatch(t -> t.id().equals(resp.id()));
    }

    // T-TRX-EX-01 — USD transaction with exchangeRate provided in request uses that value
    @Test
    void transaccionUsdConExchangeRateEnRequestUsaEseValor() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        BigDecimal rate = new BigDecimal("1200.50");
        var req = TestDataFactory.transactionRequestWithCurrency(
                catId, new BigDecimal("100.00"), TransactionType.EXPENSE,
                LocalDate.now(), "USD", rate);

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);

        // amount_cents = 10000 (100.00 * 100)
        assertThat(resp.amountCents()).isEqualTo(10000L);
        // exchange_rate should be the one we sent
        assertThat(resp.exchangeRate()).isEqualByComparingTo(rate);
        // amount_ars_cents = round(10000 * 1200.50) = 12005000
        assertThat(resp.amountArsCents()).isEqualTo(12005000L);
        assertThat(resp.currency()).isEqualTo("USD");
    }

    // T-TRX-EX-02 — USD transaction without exchangeRate looks up by transaction date, not today
    @Test
    void transaccionUsdSinRateBuscaCotizacionPorFechaDeTransaccion() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        LocalDate date1 = LocalDate.now().minusDays(5);
        LocalDate date2 = LocalDate.now().minusDays(3);

        // Insert two different exchange rates for two different dates
        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "USD", "ARS", new BigDecimal("1100.000000"), date1, "blue");
        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "USD", "ARS", new BigDecimal("1200.000000"), date2, "blue");

        // Create transaction for date1 (should use rate 1100)
        var req1 = TestDataFactory.transactionRequestWithCurrency(
                catId, new BigDecimal("10.00"), TransactionType.EXPENSE, date1, "USD", null);
        var result1 = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req1)))
                .andExpect(status().isCreated())
                .andReturn();
        var resp1 = objectMapper.readValue(result1.getResponse().getContentAsString(), TransactionResponse.class);

        // Create transaction for date2 (should use rate 1200)
        var req2 = TestDataFactory.transactionRequestWithCurrency(
                catId, new BigDecimal("10.00"), TransactionType.EXPENSE, date2, "USD", null);
        var result2 = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req2)))
                .andExpect(status().isCreated())
                .andReturn();
        var resp2 = objectMapper.readValue(result2.getResponse().getContentAsString(), TransactionResponse.class);

        // amount_cents = 1000 for both
        assertThat(resp1.amountCents()).isEqualTo(1000L);
        assertThat(resp2.amountCents()).isEqualTo(1000L);

        // resp1: amount_ars_cents = round(1000 * 1100) = 1100000
        assertThat(resp1.exchangeRate()).isEqualByComparingTo(new BigDecimal("1100"));
        assertThat(resp1.amountArsCents()).isEqualTo(1100000L);

        // resp2: amount_ars_cents = round(1000 * 1200) = 1200000
        assertThat(resp2.exchangeRate()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(resp2.amountArsCents()).isEqualTo(1200000L);
    }

    // T-TRX-EX-03 — USD transaction without exchangeRate and no rate in DB returns 422
    @Test
    void transaccionUsdSinCotizacionDisponibleDevuelve422() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // No exchange rates inserted for this date
        var req = TestDataFactory.transactionRequestWithCurrency(
                catId, new BigDecimal("50.00"), TransactionType.EXPENSE,
                LocalDate.now().minusDays(10), "USD", null);

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("MISSING_EXCHANGE_RATE");
        assertThat(body).contains("exchangeRate");
    }

    // T-TRX-EX-04 — ARS transaction ignores exchange rate lookup, rate = 1.0 always
    @Test
    void transaccionArsIgnoraLookupRateEsUno() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        var req = TestDataFactory.transactionRequestWithCurrency(
                catId, new BigDecimal("75.00"), TransactionType.EXPENSE,
                LocalDate.now(), "ARS", null);

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var resp = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
        assertThat(resp.exchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(resp.amountArsCents()).isEqualTo(resp.amountCents());
        assertThat(resp.amountArsCents()).isEqualTo(7500L);
    }

    // T-TRX-17 — Pagination: SQL LIMIT, not in-memory
    @Test
    void paginacionReal_300Registros_limit50_devuelve50ConTotal300() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // Bulk-insert 300 transactions directly via JDBC for speed
        UUID userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users LIMIT 1", UUID.class);
        for (int i = 0; i < 300; i++) {
            jdbcTemplate.update("""
                INSERT INTO transactions (id, user_id, category_id, type, amount_cents, currency,
                    amount_ars_cents, exchange_rate, description, transaction_date, source)
                VALUES (gen_random_uuid(), ?, ?, 'EXPENSE', 1000, 'ARS',
                    1000, 1.0, ?, ?, 'MANUAL')
                """,
                    userId, catId, "Bulk txn " + i,
                    LocalDate.of(2026, 6, 1).plusDays(i % 28));
        }

        // Verify count in DB
        int dbCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND deleted_at IS NULL",
                Integer.class, userId);
        assertThat(dbCount).isEqualTo(300);

        // Request with limit=50
        var result = mockMvc.perform(get("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();

        var listResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), TransactionListResponse.class);

        // Exactly 50 rows returned
        assertThat(listResponse.data()).hasSize(50);
        // Total reflects all 300 in DB
        assertThat(listResponse.total()).isEqualTo(300);
        // Limit echoed back
        assertThat(listResponse.limit()).isEqualTo(50);
        assertThat(listResponse.offset()).isEqualTo(0);
    }

    // T-TRX-PATCH-01 — PATCH amount on USD transaction recalculates amount_ars_cents with correct rate
    @Test
    void patchAmountEnUsdRecalculaConRateCorrecto() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();
        LocalDate date = LocalDate.now().minusDays(1);

        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "USD", "ARS", new BigDecimal("1150.000000"), date, "blue");

        // Create USD transaction with explicit rate
        var createReq = TestDataFactory.transactionRequestWithCurrency(
                catId, new BigDecimal("100.00"), TransactionType.EXPENSE, date, "USD", new BigDecimal("1150"));
        var created = objectMapper.readValue(
                mockMvc.perform(post("/api/transactions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(createReq)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        // PATCH amount to 200.00 — no exchangeRate in request, should lookup from DB
        var patched = objectMapper.readValue(
                mockMvc.perform(patch("/api/transactions/" + created.id())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new UpdateTransactionRequest(
                                        new BigDecimal("200.00"), null, null, null, null, null, null))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(patched.amountCents()).isEqualTo(20000L);
        assertThat(patched.exchangeRate()).isEqualByComparingTo(new BigDecimal("1150"));
        // 20000 * 1150 = 23000000
        assertThat(patched.amountArsCents()).isEqualTo(23000000L);
    }

    // T-TRX-PATCH-02 — PATCH transactionDate on USD re-resolves rate for the new date
    @Test
    void patchFechaEnUsdReResuelveRateParaFechaNueva() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();
        LocalDate dateOld = LocalDate.now().minusDays(5);
        LocalDate dateNew = LocalDate.now().minusDays(3);

        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "USD", "ARS", new BigDecimal("1000.000000"), dateOld, "blue");
        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "USD", "ARS", new BigDecimal("1300.000000"), dateNew, "blue");

        // Create USD transaction on dateOld
        var created = objectMapper.readValue(
                mockMvc.perform(post("/api/transactions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(TestDataFactory.transactionRequestWithCurrency(
                                        catId, new BigDecimal("50.00"), TransactionType.EXPENSE, dateOld, "USD", null))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(created.exchangeRate()).isEqualByComparingTo(new BigDecimal("1000"));

        // PATCH transactionDate to dateNew — rate should change to 1300
        var patched = objectMapper.readValue(
                mockMvc.perform(patch("/api/transactions/" + created.id())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new UpdateTransactionRequest(
                                        null, null, null, null, dateNew, null, null))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(patched.transactionDate()).isEqualTo(dateNew);
        assertThat(patched.exchangeRate()).isEqualByComparingTo(new BigDecimal("1300"));
        // 5000 * 1300 = 6500000
        assertThat(patched.amountArsCents()).isEqualTo(6500000L);
    }

    // T-TRX-PATCH-03 — PATCH currency ARS→USD resolves rate; USD→ARS sets rate to 1.0
    @Test
    void patchCurrencyResuelveOResetaRate() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();
        LocalDate date = LocalDate.now().minusDays(2);

        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "USD", "ARS", new BigDecimal("1250.000000"), date, "blue");

        // Create ARS transaction
        var created = objectMapper.readValue(
                mockMvc.perform(post("/api/transactions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(TestDataFactory.transactionRequestWithCurrency(
                                        catId, new BigDecimal("80.00"), TransactionType.EXPENSE, date, "ARS", null))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(created.exchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(created.amountArsCents()).isEqualTo(8000L);

        // PATCH currency to USD — should resolve rate from DB
        var patchedToUsd = objectMapper.readValue(
                mockMvc.perform(patch("/api/transactions/" + created.id())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new UpdateTransactionRequest(
                                        null, null, null, null, null, "USD", null))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(patchedToUsd.currency()).isEqualTo("USD");
        assertThat(patchedToUsd.exchangeRate()).isEqualByComparingTo(new BigDecimal("1250"));
        // 8000 * 1250 = 10000000
        assertThat(patchedToUsd.amountArsCents()).isEqualTo(10000000L);

        // PATCH currency back to ARS — rate resets to 1.0
        var patchedToArs = objectMapper.readValue(
                mockMvc.perform(patch("/api/transactions/" + created.id())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new UpdateTransactionRequest(
                                        null, null, null, null, null, "ARS", null))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(patchedToArs.currency()).isEqualTo("ARS");
        assertThat(patchedToArs.exchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(patchedToArs.amountArsCents()).isEqualTo(8000L);
    }

    // T-TRX-PATCH-04 — PATCH non-monetary field (description) does NOT touch amount_ars_cents or exchange_rate
    @Test
    void patchDescripcionNoTocaRateNiAmountArs() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();
        LocalDate date = LocalDate.now().minusDays(1);

        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "USD", "ARS", new BigDecimal("1100.000000"), date, "blue");

        // Create USD transaction
        var created = objectMapper.readValue(
                mockMvc.perform(post("/api/transactions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(TestDataFactory.transactionRequestWithCurrency(
                                        catId, new BigDecimal("25.00"), TransactionType.EXPENSE, date, "USD", null))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        BigDecimal originalRate = created.exchangeRate();
        Long originalArsCents = created.amountArsCents();

        // PATCH only description
        var patched = objectMapper.readValue(
                mockMvc.perform(patch("/api/transactions/" + created.id())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new UpdateTransactionRequest(
                                        null, null, "New description", null, null, null, null))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), TransactionResponse.class);

        assertThat(patched.description()).isEqualTo("New description");
        assertThat(patched.exchangeRate()).isEqualByComparingTo(originalRate);
        assertThat(patched.amountArsCents()).isEqualTo(originalArsCents);
        assertThat(patched.amountCents()).isEqualTo(created.amountCents());
    }
}
