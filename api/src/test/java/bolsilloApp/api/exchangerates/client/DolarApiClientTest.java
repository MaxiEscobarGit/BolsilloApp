package bolsilloApp.api.exchangerates.client;

import bolsilloApp.api.exchangerates.config.ExchangeRateProperties;
import bolsilloApp.api.exchangerates.config.ExchangeRatesConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el cliente contra un servidor HTTP local (sin red real ni Docker).
 */
class DolarApiClientTest {

    private static final String SAMPLE = """
            [
              {"moneda":"USD","casa":"oficial","nombre":"Oficial","compra":1020.5,"venta":1060.5,"fechaActualizacion":"2026-11-06T18:00:00.000Z"},
              {"moneda":"USD","casa":"blue","nombre":"Blue","compra":1180,"venta":1205,"fechaActualizacion":"2026-11-07T02:30:00.000Z"},
              {"moneda":"USD","casa":"bolsa","nombre":"Bolsa","compra":1150.1,"venta":1160.25,"fechaActualizacion":"2026-11-06T18:00:00.000Z"},
              {"moneda":"USD","casa":"contadoconliqui","nombre":"CCL","compra":1170,"venta":1175,"fechaActualizacion":"2026-11-06T18:00:00.000Z"},
              {"moneda":"USD","casa":"tarjeta","nombre":"Tarjeta","compra":1326,"venta":1378.65,"fechaActualizacion":"2026-11-06T18:00:00.000Z"}
            ]
            """;

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>(SAMPLE);
    private final AtomicInteger delayMs = new AtomicInteger(0);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/dolares", exchange -> {
            try {
                if (delayMs.get() > 0) Thread.sleep(delayMs.get());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private DolarApiClient client(Duration timeout) {
        var props = new ExchangeRateProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                List.of("oficial", "blue", "mep", "tarjeta"),
                timeout, "America/Argentina/Buenos_Aires", false);
        var webClient = new ExchangeRatesConfig().exchangeRatesWebClient(props);
        var clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);
        return new DolarApiClient(webClient, JsonMapper.builder().build(), props, clock);
    }

    @Test
    void parseaVentaMapeaBolsaAMepYFiltraFuentes() {
        List<FetchedRate> rates = client(Duration.ofSeconds(5)).fetchUsdArsRates();

        assertThat(rates).extracting(FetchedRate::source)
                .containsExactlyInAnyOrder("oficial", "blue", "mep", "tarjeta");
        assertThat(rates).filteredOn(r -> r.source().equals("mep")).singleElement()
                .satisfies(r -> assertThat(r.rate()).isEqualByComparingTo("1160.25"));
        assertThat(rates).filteredOn(r -> r.source().equals("tarjeta")).singleElement()
                .satisfies(r -> assertThat(r.rate()).isEqualByComparingTo(new BigDecimal("1378.65")));
        // 2026-11-07T02:30Z es 2026-11-06 23:30 en Buenos Aires
        assertThat(rates).filteredOn(r -> r.source().equals("blue")).singleElement()
                .satisfies(r -> assertThat(r.rateDate()).isEqualTo(LocalDate.of(2026, 11, 6)));
    }

    @Test
    void sinFechaActualizacionUsaHoyEnBuenosAires() {
        body.set("""
                [{"moneda":"USD","casa":"blue","venta":"1200.50"}]
                """);
        List<FetchedRate> rates = client(Duration.ofSeconds(5)).fetchUsdArsRates();
        assertThat(rates).containsExactly(
                new FetchedRate("blue", new BigDecimal("1200.50"), LocalDate.of(2026, 11, 8)));
    }

    @Test
    void errorHttpDevuelveListaVacia() {
        status.set(500);
        body.set("{\"error\":\"boom\"}");
        assertThat(client(Duration.ofSeconds(5)).fetchUsdArsRates()).isEmpty();
    }

    @Test
    void respuestaInvalidaDevuelveListaVacia() {
        body.set("<html>no es json</html>");
        assertThat(client(Duration.ofSeconds(5)).fetchUsdArsRates()).isEmpty();
    }

    @Test
    void timeoutDevuelveListaVacia() {
        delayMs.set(1500);
        long start = System.nanoTime();
        assertThat(client(Duration.ofMillis(300)).fetchUsdArsRates()).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1400));
    }

    @Test
    void servidorCaidoDevuelveListaVacia() {
        server.stop(0);
        assertThat(client(Duration.ofSeconds(2)).fetchUsdArsRates()).isEmpty();
    }
}
