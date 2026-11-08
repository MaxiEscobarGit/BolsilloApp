package bolsilloApp.api.exchangerates.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateResponse(
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        LocalDate rateDate,
        String source
) {
}
