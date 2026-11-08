package bolsilloApp.api.exchangerates;

import bolsilloApp.api.exchangerates.client.DolarApiClient;
import bolsilloApp.api.exchangerates.client.FetchedRate;
import bolsilloApp.api.exchangerates.config.ExchangeRateProperties;
import bolsilloApp.api.exchangerates.dto.ExchangeRateResponse;
import bolsilloApp.api.exchangerates.model.ExchangeRate;
import bolsilloApp.api.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    static final String USD = "USD";
    static final String ARS = "ARS";
    private static final String DEFAULT_SOURCE = "blue";
    private static final int DEFAULT_HISTORY_DAYS = 30;

    private final ExchangeRateRepository exchangeRateRepository;
    private final DolarApiClient dolarApiClient;
    private final ExchangeRateProperties properties;

    /**
     * Devuelve la cotización de la fecha pedida para la fuente indicada. Si no hay cotización
     * exacta para esa fecha (fin de semana, feriado, job caído), usa la más reciente anterior.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getRate(String from, String to, LocalDate date, String source) {
        return exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyAndSourceAndRateDateLessThanEqualOrderByRateDateDesc(
                        from, to, normalizeSource(source), date)
                .map(er -> {
                    if (!er.getRateDate().equals(date)) {
                        log.debug("Sin cotización {}/{} ({}) para {}; usando la del {}",
                                from, to, source, date, er.getRateDate());
                    }
                    return er.getRate();
                });
    }

    @Transactional(readOnly = true)
    public ExchangeRateResponse getLatest(String source) {
        String src = normalizeSource(source);
        return exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyAndSourceOrderByRateDateDesc(USD, ARS, src)
                .map(ExchangeRateService::toResponse)
                .orElseThrow(() -> new ApiException(
                        "No exchange rate available for source: " + src, "NOT_FOUND", 404));
    }

    /**
     * Historial USD→ARS entre {@code fromDate} y {@code toDate} (inclusive). Sin fechas devuelve
     * los últimos 30 días. Sin fuente devuelve todas las fuentes.
     */
    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> getHistory(LocalDate fromDate, LocalDate toDate, String source) {
        LocalDate end = toDate != null ? toDate : today();
        LocalDate start = fromDate != null ? fromDate : end.minusDays(DEFAULT_HISTORY_DAYS);
        if (start.isAfter(end)) {
            throw new ApiException("'from' must be before or equal to 'to'", "INVALID_RANGE", 422);
        }
        if (start.plusYears(1).isBefore(end)) {
            throw new ApiException("Date range cannot exceed 1 year", "INVALID_RANGE", 422);
        }

        List<ExchangeRate> rates = (source == null || source.isBlank())
                ? exchangeRateRepository.findByFromCurrencyAndToCurrencyAndRateDateBetweenOrderByRateDateDescSourceAsc(
                        USD, ARS, start, end)
                : exchangeRateRepository.findByFromCurrencyAndToCurrencyAndSourceAndRateDateBetweenOrderByRateDateDesc(
                        USD, ARS, normalizeSource(source), start, end);
        return rates.stream().map(ExchangeRateService::toResponse).toList();
    }

    /**
     * Trae las cotizaciones de la API externa y hace upsert por (from, to, date, source).
     * Si la API falla no lanza excepción: devuelve 0.
     *
     * @return cantidad de cotizaciones guardadas/actualizadas
     */
    @Transactional
    public int refreshFromApi() {
        List<FetchedRate> fetched = dolarApiClient.fetchUsdArsRates();
        int saved = 0;
        for (FetchedRate r : fetched) {
            exchangeRateRepository.upsert(USD, ARS, r.rate(), r.rateDate(), r.source());
            saved++;
        }
        if (saved > 0) {
            log.info("Cotizaciones USD/ARS actualizadas: {}", fetched);
        } else {
            log.warn("No se actualizó ninguna cotización USD/ARS (la API no devolvió datos)");
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean hasRatesForToday() {
        return exchangeRateRepository.existsByFromCurrencyAndToCurrencyAndRateDate(USD, ARS, today());
    }

    LocalDate today() {
        return LocalDate.now(ZoneId.of(properties.zone()));
    }

    private static String normalizeSource(String source) {
        return (source == null || source.isBlank()) ? DEFAULT_SOURCE : source.trim().toLowerCase(Locale.ROOT);
    }

    private static ExchangeRateResponse toResponse(ExchangeRate er) {
        return new ExchangeRateResponse(
                er.getFromCurrency(), er.getToCurrency(), er.getRate(), er.getRateDate(), er.getSource());
    }
}
