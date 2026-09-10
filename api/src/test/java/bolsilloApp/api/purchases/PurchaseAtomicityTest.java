package bolsilloApp.api.purchases;

import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import bolsilloApp.api.transactions.TransactionRepository;
import bolsilloApp.api.transactions.model.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

/**
 * T-PUR-05 -- Real atomicity test for PurchaseService.
 *
 * This test is in its own class because it uses @MockitoSpyBean on TransactionRepository,
 * which would contaminate other purchase tests that need the real repository.
 *
 * The test verifies that when saving transactions fails (after the purchase row is already
 * persisted), the @Transactional annotation on PurchaseService.create() rolls back
 * EVERYTHING -- including the purchase row that was saved before the failure point.
 *
 * If @Transactional is removed from PurchaseService.create(), this test WILL FAIL because
 * the purchase row will persist even though the transaction saves failed.
 */
class PurchaseAtomicityTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private TransactionRepository transactionRepository;

    @Test
    void atomicidadRealSiFallaSaveAllNoQuedaNiPurchaseNiTransactions() throws Exception {
        String token = registerAndGetToken();
        UUID catId = getSystemExpenseCategoryId();

        // Count existing rows before the attempt
        int purchasesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class);
        int txsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);

        // Configure the spy: when saveAll(Iterable<Transaction>) is called, throw RuntimeException.
        // At this point in PurchaseService.create(), the purchase row has ALREADY been saved via
        // purchaseRepository.save(purchase). Without @Transactional, that purchase row would persist.
        // With @Transactional, the entire method is rolled back.
        doThrow(new RuntimeException("Simulated DB failure on transaction batch save"))
                .when(transactionRepository).saveAll(any(Iterable.class));

        // Attempt to create a purchase with 5 installments
        var req = TestDataFactory.purchaseRequest(catId, new BigDecimal("500.00"), 5);

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isInternalServerError());

        // Verify that NOTHING was persisted -- both the purchase row and any transaction rows
        // should be rolled back thanks to @Transactional on PurchaseService.create()
        int purchasesAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class);
        int txsAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);

        assertThat(purchasesAfter).as("No new purchases should exist after rollback")
                .isEqualTo(purchasesBefore);
        assertThat(txsAfter).as("No new transactions should exist after rollback")
                .isEqualTo(txsBefore);
    }
}
