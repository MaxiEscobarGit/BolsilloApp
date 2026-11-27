package bolsilloApp.api.chat;

import bolsilloApp.api.chat.dto.AiPromptContext;
import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.shared.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Armado del request y parseo de la respuesta de Gemini contra un servidor HTTP local (sin red real).
 */
class AIServiceGeminiTest {

    private static final String GEMINI_OK = """
            {
              "candidates": [{"content": {"role": "model", "parts": [
                {"text": "{\\"message\\": \\"Listo, anote $1234,56 en Comida\\", \\"intent\\": \\"REGISTER\\", \\"transaction\\": {\\"amount\\": 1234.56, \\"currency\\": \\"ARS\\", \\"categoryName\\": \\"Comida\\", \\"description\\": \\"Almuerzo\\", \\"type\\": \\"expense\\", \\"date\\": \\"2026-11-20\\"}}"}
              ]}, "finishReason": "STOP"}],
              "usageMetadata": {"promptTokenCount": 300, "candidatesTokenCount": 60, "totalTokenCount": 360}
            }
            """;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>(GEMINI_OK);
    private final AtomicInteger status = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
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

    private AIService gemini() {
        WebClient client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .defaultHeader("x-goog-api-key", "test-key")
                .build();
        return new AIService(WebClient.create(), client, mapper, "gemini",
                "claude-haiku-4-5", "claude-sonnet-5-5", "gemini-fast", "gemini-smart");
    }

    private AiPromptContext context() {
        MetricsContext metrics = new MetricsContext(0L, 0L, 0L, Map.of(), Map.of(), List.of(), List.of());
        return new AiPromptContext(metrics, List.of(), List.of("Comida"), List.of("Sueldo"), null, LocalDate.of(2026, 11, 20));
    }

    @Test
    void armaElRequestDeGeminiYParseaLaRespuesta() throws Exception {
        AIService.AiResponse r = gemini().call("gaste 1234,56 en comida", ChatIntent.REGISTER, context());

        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-fast:generateContent");
        assertThat(apiKeyHeader.get()).isEqualTo("test-key");
        JsonNode body = mapper.readTree(requestBody.get());
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asString()).contains("asistente financiero");
        assertThat(body.path("contents").path(0).path("role").asString()).isEqualTo("user");
        assertThat(body.path("contents").path(0).path("parts").path(0).path("text").asString()).isEqualTo("gaste 1234,56 en comida");
        assertThat(body.path("generationConfig").path("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(body.path("generationConfig").path("maxOutputTokens").asInt()).isPositive();

        assertThat(r.modelUsed()).isEqualTo("gemini-fast");
        assertThat(r.tokensUsed()).isEqualTo(360);
        assertThat(r.intent()).isEqualTo(ChatIntent.REGISTER);
        assertThat(r.message()).contains("Listo");
        assertThat(r.transactionData().amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(r.transactionData().categoryName()).isEqualTo("Comida");
    }

    @Test
    void queryUsaElModeloSmart() {
        gemini().call("¿cuanto gaste?", ChatIntent.QUERY, context());

        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-smart:generateContent");
    }

    @Test
    void completeNoPideJsonYDevuelveElTexto() throws Exception {
        responseBody.set("""
                {"candidates": [{"content": {"parts": [
                  {"text": "pensando", "thought": true}, {"text": " Resumen del mes. "}]}}]}
                """);

        String text = gemini().complete("sistema", "resumi");

        assertThat(text).isEqualTo("Resumen del mes.");
        JsonNode config = mapper.readTree(requestBody.get()).path("generationConfig");
        assertThat(config.has("responseMimeType")).isFalse();
    }

    @Test
    void respuestaSinCandidatosOErrorHttpDa503() {
        responseBody.set("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}");
        assertThatThrownBy(() -> gemini().call("hola", ChatIntent.CHAT, context()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("AI_UNAVAILABLE"));

        status.set(429);
        responseBody.set("{\"error\": {\"code\": 429}}");
        assertThatThrownBy(() -> gemini().call("hola", ChatIntent.CHAT, context()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
    }

    @Test
    void proveedorInvalidoNoArranca() {
        assertThatThrownBy(() -> new AIService(WebClient.create(), WebClient.create(), mapper, "openai", "a", "b", "c", "d"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
