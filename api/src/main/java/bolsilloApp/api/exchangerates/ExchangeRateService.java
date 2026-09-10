package bolsilloApp.api.exchangerates;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    public Optional<BigDecimal> getRate(String from, String to, LocalDate date, String source) {
        return exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndRateDateAndSource(from, to, date, source)
                .map(er -> er.getRate());
    }
}
