package bolsilloApp.api.exchangerates.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cotización USD→ARS ya normalizada (fuente interna + valor de venta + fecha). */
public record FetchedRate(String source, BigDecimal rate, LocalDate rateDate) {
}
