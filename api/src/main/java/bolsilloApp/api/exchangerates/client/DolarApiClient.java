package bolsilloApp.api.exchangerates.client;

import bolsilloApp.api.exchangerates.config.ExchangeRateProperties;
import bolsilloApp.api.exchangerates.config.ExchangeRatesConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cliente de https://dolarapi.com ({@code GET /v1/dolares}). Nunca lanza excepciones: si la API
 * falla, tarda demasiado o responde algo inválido, loguea y devuelve una lista vacía.
 */
@Component
@Slf4j
public class DolarApiClient {

    static final String PATH = "/v1/dolares";

    /** Nombre de la "casa" en dolarapi → nombre de la fuente en exchange_rates. */
    private static final Map<String, String> CASA_TO_SOURCE = Map.of(
            "bolsa", "mep",
            "contadoconliqui", "ccl");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ExchangeRateProperties properties;
    private final Clock clock;

    public DolarApiClient(@Qualifier(ExchangeRatesConfig.WEB_CLIENT) WebClient webClient,
                          ObjectMapper objectMapper,
                          ExchangeRateProperties properties) {
        this(webClient, objectMapper, properties, Clock.systemUTC());
    }

    DolarApiClient(WebClient webClient, ObjectMapper objectMapper,
                   ExchangeRateProperties properties, Clock clock) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Devuelve las cotizaciones USD→ARS (valor de venta) de las fuentes configuradas. */
    public List<FetchedRate> fetchUsdArsRates() {
        String body;
        try {
            body = webClient.get()
                    .uri(PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(properties.timeout())
                    .block();
        } catch (Exception e) {
            log.warn("No se pudieron obtener cotizaciones de {}{}: {}",
                    properties.baseUrl(), PATH, e.toString());
            return List.of();
        }
        return parse(body);
    }

    List<FetchedRate> parse(String body) {
        if (body == null || body.isBlank()) {
            log.warn("Respuesta vacía de la API de cotizaciones");
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Respuesta inválida de la API de cotizaciones: {}", e.getMessage());
            return List.of();
        }
        if (root == null || !root.isArray()) {
            log.warn("Respuesta inesperada de la API de cotizaciones (se esperaba un array)");
            return List.of();
        }

        ZoneId zone = ZoneId.of(properties.zone());
        Set<String> wanted = Set.copyOf(properties.sources().stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).toList());
        List<FetchedRate> result = new ArrayList<>();

        for (JsonNode node : root) {
            String casa = text(node, "casa");
            if (casa == null) continue;
            String moneda = text(node, "moneda");
            if (moneda != null && !"USD".equalsIgnoreCase(moneda)) continue;

            String source = CASA_TO_SOURCE.getOrDefault(casa.toLowerCase(Locale.ROOT), casa.toLowerCase(Locale.ROOT));
            if (!wanted.contains(source)) continue;

            BigDecimal venta = decimal(node.get("venta"));
            if (venta == null || venta.signum() <= 0) {
                log.warn("Cotización sin valor de venta válido para la fuente {}", source);
                continue;
            }
            result.add(new FetchedRate(source, venta, rateDate(text(node, "fechaActualizacion"), zone)));
        }
        return result;
    }

    private LocalDate rateDate(String fechaActualizacion, ZoneId zone) {
        if (fechaActualizacion != null) {
            try {
                return OffsetDateTime.parse(fechaActualizacion).atZoneSameInstant(zone).toLocalDate();
            } catch (Exception e) {
                log.debug("fechaActualizacion no parseable: {}", fechaActualizacion);
            }
        }
        return LocalDate.now(clock.withZone(zone));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asString();
    }

    private static BigDecimal decimal(JsonNode v) {
        if (v == null || v.isNull()) return null;
        try {
            return v.isNumber() ? v.decimalValue() : new BigDecimal(v.asString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
