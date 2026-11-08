package bolsilloApp.api.exchangerates;

import bolsilloApp.api.exchangerates.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndRateDateAndSource(
            String fromCurrency, String toCurrency, LocalDate rateDate, String source);

    /** Cotización exacta de la fecha o, si no existe, la más reciente anterior (misma fuente). */
    Optional<ExchangeRate> findFirstByFromCurrencyAndToCurrencyAndSourceAndRateDateLessThanEqualOrderByRateDateDesc(
            String fromCurrency, String toCurrency, String source, LocalDate rateDate);

    Optional<ExchangeRate> findFirstByFromCurrencyAndToCurrencyAndSourceOrderByRateDateDesc(
            String fromCurrency, String toCurrency, String source);

    List<ExchangeRate> findByFromCurrencyAndToCurrencyAndSourceAndRateDateBetweenOrderByRateDateDesc(
            String fromCurrency, String toCurrency, String source, LocalDate fromDate, LocalDate toDate);

    List<ExchangeRate> findByFromCurrencyAndToCurrencyAndRateDateBetweenOrderByRateDateDescSourceAsc(
            String fromCurrency, String toCurrency, LocalDate fromDate, LocalDate toDate);

    boolean existsByFromCurrencyAndToCurrencyAndRateDate(String fromCurrency, String toCurrency, LocalDate rateDate);

    /** Upsert por la clave única (from_currency, to_currency, rate_date, source). */
    @Modifying
    @Query(value = """
            INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source)
            VALUES (:fromCurrency, :toCurrency, :rate, :rateDate, :source)
            ON CONFLICT (from_currency, to_currency, rate_date, source)
            DO UPDATE SET rate = EXCLUDED.rate
            """, nativeQuery = true)
    int upsert(@Param("fromCurrency") String fromCurrency,
               @Param("toCurrency") String toCurrency,
               @Param("rate") BigDecimal rate,
               @Param("rateDate") LocalDate rateDate,
               @Param("source") String source);
}
