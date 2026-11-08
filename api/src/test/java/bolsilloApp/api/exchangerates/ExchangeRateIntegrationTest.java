package bolsilloApp.api.exchangerates;

import bolsilloApp.api.exchangerates.client.DolarApiClient;
import bolsilloApp.api.exchangerates.client.FetchedRate;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import bolsilloApp.api.transactions.TransactionType;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExchangeRateIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    @MockitoBean
    private DolarApiClient dolarApiClient;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private ExchangeRateSyncJob syncJob;

    private void insertRate(String source, String rate, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source) VALUES ('USD','ARS',?,?,?)",
                new BigDecimal(rate), date, source);
    }

    private BigDecimal storedRate(String source, LocalDate date) {
        return jdbcTemplate.queryForObject(
                "SELECT rate FROM exchange_rates WHERE from_currency='USD' AND to_currency='ARS' AND source=? AND rate_date=?",
                BigDecimal.class, source, date);
    }

    private int countRates() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exchange_rates", Integer.class);
    }

    // -- Upsert --

    @Test
    void refreshInsertaYLuegoActualizaSinDuplicar() {
        LocalDate today = LocalDate.now(AR);
        when(dolarApiClient.fetchUsdArsRates()).thenReturn(List.of(
                new FetchedRate("blue", new BigDecimal("1200.00"), today),
                new FetchedRate("oficial", new BigDecimal("1000.00"), today)));

        assertThat(exchangeRateService.refreshFromApi()).isEqualTo(2);
        assertThat(countRates()).isEqualTo(2);

        when(dolarApiClient.fetchUsdArsRates()).thenReturn(List.of(
                new FetchedRate("blue", new BigDecimal("1250.50"), today),
                new FetchedRate("mep", new BigDecimal("1180.00"), today)));

        assertThat(exchangeRateService.refreshFromApi()).isEqualTo(2);
        assertThat(countRates()).isEqualTo(3);
        assertThat(storedRate("blue", today)).isEqualByComparingTo("1250.50");
        assertThat(storedRate("oficial", today)).isEqualByComparingTo("1000.00");
    }

    @Test
    void apiCaidaNoRompeNiBorraDatos() {
        LocalDate today = LocalDate.now(AR);
        insertRate("blue", "1200", today);

        when(dolarApiClient.fetchUsdArsRates()).thenReturn(List.of());
        assertThat(exchangeRateService.refreshFromApi()).isZero();

        when(dolarApiClient.fetchUsdArsRates()).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> syncJob.syncScheduled()).doesNotThrowAnyException();

        assertThat(storedRate("blue", today)).isEqualByComparingTo("1200");
    }

    // -- Carga al arrancar --

    @Test
    void cargaAlArrancarSiNoHayCotizacionDelDia() {
        LocalDate today = LocalDate.now(AR);
        when(dolarApiClient.fetchUsdArsRates()).thenReturn(List.of(
                new FetchedRate("blue", new BigDecimal("1200"), today)));

        syncJob.loadIfMissingForToday();

        verify(dolarApiClient, times(1)).fetchUsdArsRates();
        assertThat(storedRate("blue", today)).isEqualByComparingTo("1200");
    }

    @Test
    void noCargaAlArrancarSiYaHayCotizacionDelDia() {
        insertRate("oficial", "1000", LocalDate.now(AR));

        syncJob.loadIfMissingForToday();

        verify(dolarApiClient, never()).fetchUsdArsRates();
    }

    // -- Fallback --

    @Test
    void getRateUsaLaMasRecienteAnteriorDeLaMismaFuente() {
        LocalDate date = LocalDate.of(2026, 11, 9);
        insertRate("blue", "1100", date.minusDays(10));
        insertRate("blue", "1150", date.minusDays(3));
        insertRate("oficial", "999", date.minusDays(1));
        insertRate("blue", "1300", date.plusDays(1));

        assertThat(exchangeRateService.getRate("USD", "ARS", date, "blue")).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("1150"));
        assertThat(exchangeRateService.getRate("USD", "ARS", date.minusDays(3), "blue")).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("1150"));
        assertThat(exchangeRateService.getRate("USD", "ARS", date.minusDays(11), "blue")).isEmpty();
        assertThat(exchangeRateService.getRate("USD", "ARS", date, "mep")).isEmpty();
    }

    @Test
    void transaccionEnUsdSinExchangeRateUsaFallback() throws Exception {
        String token = registerAndGetToken();
        LocalDate today = LocalDate.now();
        insertRate("blue", "1200", today.minusDays(2));

        var result = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.transactionRequestWithCurrency(
                                getSystemExpenseCategoryId(), new BigDecimal("10.00"),
                                TransactionType.EXPENSE, today, "USD", null))))
                .andExpect(status().isCreated())
                .andReturn();

        var tx = objectMapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
        assertThat(tx.exchangeRate()).isEqualByComparingTo("1200");
        assertThat(tx.amountArsCents()).isEqualTo(1_200_000L);
    }

    // -- Endpoints --

    @Test
    void latestDevuelveLaUltimaCotizacionDeLaFuente() throws Exception {
        String token = registerAndGetToken();
        insertRate("blue", "1100", LocalDate.of(2026, 11, 2));
        insertRate("blue", "1210.5", LocalDate.of(2026, 11, 6));
        insertRate("oficial", "1000", LocalDate.of(2026, 11, 7));

        mockMvc.perform(get("/api/exchange-rates/latest")
                        .header("Authorization", "Bearer " + token)
                        .param("source", "blue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.toCurrency").value("ARS"))
                .andExpect(jsonPath("$.source").value("blue"))
                .andExpect(jsonPath("$.rateDate").value("2026-11-06"))
                .andExpect(jsonPath("$.rate").value(1210.5))
                .andExpect(jsonPath("$.id").doesNotExist());

        // Sin source usa la fuente preferida del usuario (blue por defecto)
        mockMvc.perform(get("/api/exchange-rates/latest")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("blue"));

        mockMvc.perform(get("/api/exchange-rates/latest")
                        .header("Authorization", "Bearer " + token)
                        .param("source", "mep"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void historialFiltraPorFechasYFuente() throws Exception {
        String token = registerAndGetToken();
        insertRate("blue", "1100", LocalDate.of(2026, 10, 1));
        insertRate("blue", "1150", LocalDate.of(2026, 11, 3));
        insertRate("blue", "1200", LocalDate.of(2026, 11, 6));
        insertRate("oficial", "1000", LocalDate.of(2026, 11, 6));

        mockMvc.perform(get("/api/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-08")
                        .param("source", "blue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rateDate").value("2026-11-06"))
                .andExpect(jsonPath("$[1].rateDate").value("2026-11-03"));

        mockMvc.perform(get("/api/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-11-08")
                        .param("to", "2026-11-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void endpointsRequierenJwt() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/latest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/exchange-rates"))
                .andExpect(status().isUnauthorized());
    }
}
