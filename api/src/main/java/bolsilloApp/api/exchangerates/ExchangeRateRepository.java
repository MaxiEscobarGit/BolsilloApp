package bolsilloApp.api.exchangerates;

import bolsilloApp.api.exchangerates.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndRateDateAndSource(
            String fromCurrency, String toCurrency, LocalDate rateDate, String source);
}
