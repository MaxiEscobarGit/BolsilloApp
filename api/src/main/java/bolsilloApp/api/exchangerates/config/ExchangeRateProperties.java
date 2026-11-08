package bolsilloApp.api.exchangerates.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuración del módulo de tipo de cambio (bloque {@code # Exchange rates} en application.properties).
 *
 * @param baseUrl       URL base de la API pública (por defecto https://dolarapi.com)
 * @param sources       fuentes a persistir (oficial, blue, mep, tarjeta, ...)
 * @param timeout       timeout total de cada llamada a la API externa
 * @param zone          zona horaria usada para determinar "la fecha de hoy" y para el cron
 * @param loadOnStartup si es true, al arrancar se cargan cotizaciones cuando no hay ninguna del día
 */
@ConfigurationProperties(prefix = "exchange-rates")
public record ExchangeRateProperties(
        String baseUrl,
        List<String> sources,
        Duration timeout,
        String zone,
        boolean loadOnStartup
) {
    public ExchangeRateProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://dolarapi.com";
        if (sources == null || sources.isEmpty()) sources = List.of("oficial", "blue", "mep", "tarjeta");
        if (timeout == null) timeout = Duration.ofSeconds(5);
        if (zone == null || zone.isBlank()) zone = "America/Argentina/Buenos_Aires";
    }
}
