package bolsilloApp.api.chat;

import bolsilloApp.api.chat.dto.AiPromptContext;
import bolsilloApp.api.chat.dto.ChatRequest;
import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.support.AbstractIntegrationTest;
import bolsilloApp.api.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat flow end-to-end with the HTTP call to Anthropic stubbed
 * ({@link AIService#sendMessage} on a spy, so prompt building and parsing are real).
 */
class ChatIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    AIService aiService;

    private String token;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndGetToken();
        userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'test@test.com'", UUID.class);
    }

    // -- REGISTER --

    @Test
    void registerCreaTransaccionChatYGuardaTransactionsCreated() throws Exception {
        // Custom category must reach the prompt (dynamic categories)
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(TestDataFactory.categoryRequest("Mascotas"))))
                .andExpect(status().isCreated());

        stubAi(aiJson("""
                {"message":"Listo, registré $1500.50 en Supermercado","intent":"REGISTER",
                 "transaction":{"amount":1500.50,"categoryName":"Supermercado","description":"Compra super",
                                "type":"expense","date":"%s"}}
                """.formatted(LocalDate.now())));

        JsonNode body = chat("Gasté 1500,50 en el super").andExpect(status().isOk()).andReturnJson();

        assertThat(body.path("intent").asString()).isEqualTo("REGISTER");
        assertThat(body.path("message").asString()).contains("registré");
        UUID txId = UUID.fromString(body.path("data").path("transactionId").asString());

        var tx = jdbcTemplate.queryForMap("""
                SELECT t.amount_cents, t.amount_ars_cents, t.currency, t.source, t.type, c.name AS category
                FROM transactions t JOIN categories c ON c.id = t.category_id WHERE t.id = ?""", txId);
        assertThat(tx.get("amount_cents")).isEqualTo(150050L);
        assertThat(tx.get("amount_ars_cents")).isEqualTo(150050L);
        assertThat(tx.get("currency")).isEqualTo("ARS");
        assertThat(tx.get("source")).isEqualTo("CHAT");
        assertThat(tx.get("type")).isEqualTo("EXPENSE");
        assertThat(tx.get("category")).isEqualTo("Supermercado");

        UUID[] created = jdbcTemplate.queryForObject(
                "SELECT transactions_created FROM chat_messages WHERE user_id = ? AND role = 'assistant'",
                (rs, i) -> (UUID[]) rs.getArray(1).getArray(), userId);
        assertThat(created).containsExactly(txId);

        assertThat(countMessages()).isEqualTo(2);
        assertThat(aiMessagesUsed()).isEqualTo(1);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(aiService).sendMessage(eq("claude-haiku-5-5"), system.capture(), anyString());
        assertThat(system.getValue()).contains("Mascotas").contains("Supermercado").contains("Otros ingresos");
    }

    @Test
    void registerIngresoConCategoriaInexistenteUsaOtrosIngresos() throws Exception {
        stubAi(aiJson("""
                {"message":"Registré tu ingreso","intent":"REGISTER",
                 "transaction":{"amount":20000,"categoryName":"Comida","description":"Venta","type":"income"}}
                """));

        JsonNode body = chat("cobré 20000 por una venta").andExpect(status().isOk()).andReturnJson();

        assertThat(body.path("data").path("category").asString()).isEqualTo("Otros ingresos");
        assertThat(body.path("data").path("type").asString()).isEqualTo("INCOME");
    }

    @Test
    void registerEnUsdUsaTipoDeCambio() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO exchange_rates (from_currency, to_currency, rate, rate_date, source)
                VALUES ('USD', 'ARS', 1200.5, ?, 'blue')""", LocalDate.now());
        stubAi(aiJson("""
                {"message":"Registré 20 USD","intent":"REGISTER",
                 "transaction":{"amount":20,"currency":"USD","categoryName":"Entretenimiento",
                                "description":"Netflix","type":"expense"}}
                """));

        JsonNode body = chat("pagué 20 usd de netflix").andExpect(status().isOk()).andReturnJson();

        UUID txId = UUID.fromString(body.path("data").path("transactionId").asString());
        var tx = jdbcTemplate.queryForMap(
                "SELECT amount_cents, amount_ars_cents, currency FROM transactions WHERE id = ?", txId);
        assertThat(tx.get("currency")).isEqualTo("USD");
        assertThat(tx.get("amount_cents")).isEqualTo(2000L);
        assertThat(tx.get("amount_ars_cents")).isEqualTo(2401000L);
    }

    @Test
    void registerFallidoNoDiceRegistrado() throws Exception {
        // USD without exchange rate -> cannot register
        stubAi(aiJson("""
                {"message":"Listo, registrado!","intent":"REGISTER",
                 "transaction":{"amount":20,"currency":"USD","categoryName":"Otros","type":"expense"}}
                """));

        JsonNode body = chat("gasté 20 dolares").andExpect(status().isOk()).andReturnJson();

        assertThat(body.path("message").asString()).startsWith("No pude registrar").doesNotContain("registrado!");
        assertThat(body.path("data").path("error").asString()).isEqualTo("MISSING_EXCHANGE_RATE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();

        String stored = jdbcTemplate.queryForObject(
                "SELECT content FROM chat_messages WHERE user_id = ? AND role = 'assistant'", String.class, userId);
        assertThat(stored).startsWith("No pude registrar");
        assertThat(aiMessagesUsed()).isEqualTo(1);
    }

    // -- CORRECT + alias --

    @Test
    void correctActualizaUltimaTransaccionChatYHaceUpsertDeAlias() throws Exception {
        stubAi(aiJson("""
                {"message":"Registré $5000 en Comida","intent":"REGISTER",
                 "transaction":{"amount":5000,"categoryName":"Comida","description":"el chino","type":"expense"}}
                """));
        JsonNode reg = chat("gasté 5000 en el chino").andExpect(status().isOk()).andReturnJson();
        UUID txId = UUID.fromString(reg.path("data").path("transactionId").asString());

        stubAi(aiJson("""
                {"message":"Corregido: el chino es Supermercado","intent":"CORRECT",
                 "transaction":{"categoryName":"Supermercado","alias":"El Chino"}}
                """));
        JsonNode corr = chat("no era comida, el chino es supermercado").andExpect(status().isOk()).andReturnJson();

        assertThat(corr.path("intent").asString()).isEqualTo("CORRECT");
        assertThat(corr.path("data").path("transactionId").asString()).isEqualTo(txId.toString());
        assertThat(currentCategory(txId)).isEqualTo("Supermercado");
        assertThat(aliasUseCount("el chino")).isEqualTo(1);
        assertThat(aliasCategory("el chino")).isEqualTo("Supermercado");

        // The prompt for CORRECT includes only the target transaction
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(aiService, times(2)).sendMessage(anyString(), system.capture(), anyString());
        assertThat(system.getAllValues().get(1)).contains("TRANSACCION A CORREGIR").contains("el chino");

        // Correcting again (amount) increments use_count and recalculates
        stubAi(aiJson("""
                {"message":"Corregido el monto","intent":"CORRECT",
                 "transaction":{"amount":6000,"alias":"el chino"}}
                """));
        chat("me equivoqué, en el chino fueron 6000").andExpect(status().isOk());

        assertThat(aliasUseCount("el chino")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount_ars_cents FROM transactions WHERE id = ?", Long.class, txId)).isEqualTo(600000L);

        // Aliases are now part of the prompt
        assertThat(aiService.buildSystemPrompt(ChatIntent.REGISTER, new AiPromptContext(
                new MetricsContext(0L, 0L, 0L, Map.of(), Map.of(),
                        List.of(), List.of()),
                List.of(Map.of("alias", "el chino", "category", "Supermercado")),
                List.of("Supermercado"), List.of("Sueldo"), null, LocalDate.now())))
                .contains("\"el chino\" -> Supermercado");
    }

    @Test
    void correctSinTransaccionPreviaInformaError() throws Exception {
        stubAi(aiJson("""
                {"message":"Corregido","intent":"CORRECT","transaction":{"categoryName":"Supermercado"}}
                """));

        JsonNode body = chat("no era comida").andExpect(status().isOk()).andReturnJson();

        assertThat(body.path("message").asString()).doesNotContain("Corregido");
        assertThat(body.path("data").path("error").asString()).isEqualTo("NOTHING_TO_CORRECT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_aliases", Integer.class)).isZero();
    }

    @Test
    void correctConCambioDeTipoUsaCategoriaDelTipoNuevo() throws Exception {
        UUID catId = getSystemCategoryId("Comida");
        stubAi(aiJson("""
                {"message":"ok","intent":"REGISTER",
                 "transaction":{"amount":100,"categoryName":"Comida","description":"algo","type":"expense"}}
                """));
        JsonNode reg = chat("gasté 100 en algo").andExpect(status().isOk()).andReturnJson();
        UUID txId = UUID.fromString(reg.path("data").path("transactionId").asString());
        assertThat(jdbcTemplate.queryForObject("SELECT category_id FROM transactions WHERE id = ?", UUID.class, txId))
                .isEqualTo(catId);

        stubAi(aiJson("""
                {"message":"Corregido a ingreso","intent":"CORRECT","transaction":{"type":"income"}}
                """));
        chat("no era un gasto, era un ingreso").andExpect(status().isOk());

        assertThat(currentCategory(txId)).isEqualTo("Otros ingresos");
        assertThat(jdbcTemplate.queryForObject("SELECT type FROM transactions WHERE id = ?", String.class, txId))
                .isEqualTo("INCOME");
    }

    // -- Errors / quota --

    @Test
    void errorDeIaDevuelve503SinConsumirCuotaNiGuardarMensajes() throws Exception {
        doThrow(new RuntimeException("boom")).when(aiService).sendMessage(anyString(), anyString(), anyString());

        chat("gasté 500 en el super").andExpect(status().isServiceUnavailable());

        assertThat(countMessages()).isZero();
        assertThat(aiMessagesUsed()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
    }

    @Test
    void respuestaInvalidaDeIaDevuelve503() throws Exception {
        stubAi("{\"content\":[]}");

        chat("hola").andExpect(status().isServiceUnavailable());

        assertThat(aiMessagesUsed()).isZero();
    }

    @Test
    void cuotaAgotadaDevuelve429SinLlamarALaIa() throws Exception {
        jdbcTemplate.update("UPDATE users SET ai_messages_used = ? WHERE id = ?",
                ChatService.MAX_AI_MESSAGES_PER_MONTH, userId);

        chat("gasté 500 en el super").andExpect(status().isTooManyRequests());

        verify(aiService, never()).sendMessage(anyString(), anyString(), anyString());
        assertThat(countMessages()).isZero();
    }

    @Test
    void cuotaVencidaSeResetea() throws Exception {
        jdbcTemplate.update("""
                UPDATE users SET ai_messages_used = ?, ai_messages_reset_at = NOW() - INTERVAL '40 days'
                WHERE id = ?""", ChatService.MAX_AI_MESSAGES_PER_MONTH, userId);
        stubAi(aiJson("{\"message\":\"Hola!\",\"intent\":\"CHAT\",\"transaction\":null}"));

        chat("hola").andExpect(status().isOk());

        assertThat(aiMessagesUsed()).isEqualTo(1);
    }

    @Test
    void queryUsaModeloSmart() throws Exception {
        stubAi(aiJson("{\"message\":\"Gastaste $0\",\"intent\":\"QUERY\",\"transaction\":null}"));

        JsonNode body = chat("¿Cuánto gasté este mes?").andExpect(status().isOk()).andReturnJson();

        assertThat(body.path("modelUsed").asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(body.path("tokensUsed").asInt()).isEqualTo(30);
    }

    // -- helpers --

    private void stubAi(String anthropicResponse) {
        doReturn(anthropicResponse).when(aiService).sendMessage(anyString(), anyString(), anyString());
    }

    /** Wraps the model text in a Messages API response envelope. */
    private String aiJson(String modelText) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "content", List.of(Map.of("type", "text", "text", modelText.strip())),
                "usage", Map.of("input_tokens", 20, "output_tokens", 10)));
    }

    private JsonResult chat(String message) throws Exception {
        return new JsonResult(mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ChatRequest(message)))));
    }

    private final class JsonResult {
        private final ResultActions actions;

        JsonResult(ResultActions actions) {
            this.actions = actions;
        }

        JsonResult andExpect(ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        JsonNode andReturnJson() throws Exception {
            return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }

    private int countMessages() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE user_id = ?", Integer.class, userId);
    }

    private int aiMessagesUsed() {
        return jdbcTemplate.queryForObject("SELECT ai_messages_used FROM users WHERE id = ?", Integer.class, userId);
    }

    private String currentCategory(UUID txId) {
        return jdbcTemplate.queryForObject(
                "SELECT c.name FROM transactions t JOIN categories c ON c.id = t.category_id WHERE t.id = ?",
                String.class, txId);
    }

    private Integer aliasUseCount(String alias) {
        return jdbcTemplate.queryForObject(
                "SELECT use_count FROM user_aliases WHERE user_id = ? AND alias = ?", Integer.class, userId, alias);
    }

    private String aliasCategory(String alias) {
        return jdbcTemplate.queryForObject("""
                SELECT c.name FROM user_aliases a JOIN categories c ON c.id = a.category_id
                WHERE a.user_id = ? AND a.alias = ?""", String.class, userId, alias);
    }

}
