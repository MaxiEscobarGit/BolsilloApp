package bolsilloApp.api.chat;

import bolsilloApp.api.chat.dto.AiParsedResponse;
import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final WebClient anthropicWebClient;
    private final ObjectMapper objectMapper;

    private static final String HAIKU_MODEL = "claude-3-haiku-20240307";
    private static final String SONNET_MODEL = "claude-sonnet-4-20250514";

    public AiResponse call(String userMessage, ChatIntent intent, MetricsContext context,
                           List<Map<String, String>> aliases) {
        String model = (intent == ChatIntent.QUERY) ? SONNET_MODEL : HAIKU_MODEL;
        String systemPrompt = buildSystemPrompt(intent, context, aliases);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            String responseJson = anthropicWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            String textContent = root.path("content").get(0).path("text").asText();
            int tokensUsed = root.path("usage").path("input_tokens").asInt()
                    + root.path("usage").path("output_tokens").asInt();

            // Try to parse as structured JSON
            AiParsedResponse parsed = tryParseStructured(textContent, intent);

            return new AiResponse(
                    parsed != null ? parsed.message() : textContent,
                    parsed != null ? parsed.intent() : intent,
                    parsed != null ? parsed.transaction() : null,
                    model,
                    tokensUsed,
                    textContent
            );

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic API call failed", e);
            throw new ApiException("AI service temporarily unavailable", "AI_UNAVAILABLE", 503);
        }
    }

    private AiParsedResponse tryParseStructured(String text, ChatIntent intent) {
        try {
            // Try to extract JSON from the response (it may be wrapped in markdown code blocks)
            String jsonStr = text;
            if (text.contains("```json")) {
                jsonStr = text.substring(text.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            } else if (text.contains("```")) {
                jsonStr = text.substring(text.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }

            JsonNode node = objectMapper.readTree(jsonStr.trim());

            String message = node.has("message") ? node.get("message").asText() : text;
            ChatIntent parsedIntent = intent;
            if (node.has("intent")) {
                try {
                    parsedIntent = ChatIntent.valueOf(node.get("intent").asText().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            AiParsedResponse.TransactionData txData = null;
            if (node.has("transaction") && !node.get("transaction").isNull()) {
                JsonNode tx = node.get("transaction");
                txData = new AiParsedResponse.TransactionData(
                        tx.has("amount") ? new BigDecimal(tx.get("amount").asText()) : null,
                        tx.has("categoryName") ? tx.get("categoryName").asText() : null,
                        tx.has("description") ? tx.get("description").asText() : null,
                        tx.has("type") ? tx.get("type").asText() : null,
                        tx.has("date") ? LocalDate.parse(tx.get("date").asText()) : null
                );
            }

            return new AiParsedResponse(message, parsedIntent, txData);
        } catch (Exception e) {
            log.debug("Could not parse structured response from AI: {}", e.getMessage());
            return null;
        }
    }

    private String buildSystemPrompt(ChatIntent intent, MetricsContext context,
                                     List<Map<String, String>> aliases) {
        StringBuilder sb = new StringBuilder();
        sb.append("Eres un asistente financiero personal. Responde en espanol.\n\n");

        sb.append("CONTEXTO FINANCIERO DEL USUARIO (mes actual):\n");
        sb.append("- Ingresos totales: $").append(context.totalIncomeCents() / 100.0).append("\n");
        sb.append("- Gastos totales: $").append(context.totalExpenseCents() / 100.0).append("\n");
        sb.append("- Balance: $").append(context.balanceCents() / 100.0).append("\n");

        if (!context.expenseByCategory().isEmpty()) {
            sb.append("- Gastos por categoria: ");
            context.expenseByCategory().forEach((cat, amount) ->
                    sb.append(cat).append("=$").append(amount / 100.0).append(" "));
            sb.append("\n");
        }

        if (!context.activeDebts().isEmpty()) {
            sb.append("- Deudas activas: ");
            context.activeDebts().forEach(d ->
                    sb.append(d.counterpartName()).append(" (").append(d.direction()).append(") $")
                            .append(d.amountCents() / 100.0).append(" "));
            sb.append("\n");
        }

        if (!aliases.isEmpty()) {
            sb.append("\nALIAS DEL USUARIO (nombres que usa para comercios/categorias):\n");
            aliases.forEach(a -> sb.append("- \"").append(a.get("alias")).append("\" -> ")
                    .append(a.get("category")).append("\n"));
        }

        sb.append("\nINSTRUCCIONES:\n");

        switch (intent) {
            case REGISTER -> sb.append("""
                    El usuario quiere registrar un gasto o ingreso.
                    Responde UNICAMENTE con JSON valido (sin markdown):
                    {
                      "message": "texto amable confirmando el registro",
                      "intent": "REGISTER",
                      "transaction": {
                        "amount": 1234.56,
                        "categoryName": "nombre de la categoria mas apropiada",
                        "description": "descripcion breve",
                        "type": "expense o income",
                        "date": "YYYY-MM-DD (hoy si no especifica)"
                      }
                    }
                    Categorias disponibles para gastos: Comida, Supermercado, Transporte, Salud, Entretenimiento, Servicios, Ropa, Educacion, Otros
                    Categorias disponibles para ingresos: Sueldo, Freelance, Inversiones, Otros ingresos
                    """);
            case QUERY -> sb.append("""
                    El usuario pregunta sobre sus finanzas. Usa el contexto proporcionado para responder.
                    Responde con JSON: {"message": "tu respuesta detallada", "intent": "QUERY", "transaction": null}
                    """);
            case CORRECT -> sb.append("""
                    El usuario quiere corregir una transaccion reciente.
                    Responde con JSON: {"message": "confirmacion de la correccion", "intent": "CORRECT", "transaction": {datos corregidos}}
                    """);
            case CHAT -> sb.append("""
                    Conversacion general sobre finanzas.
                    Responde con JSON: {"message": "tu respuesta", "intent": "CHAT", "transaction": null}
                    """);
        }

        return sb.toString();
    }

    /**
     * Internal record to hold the full AI response with metadata.
     */
    public record AiResponse(
            String message,
            ChatIntent intent,
            AiParsedResponse.TransactionData transactionData,
            String modelUsed,
            int tokensUsed,
            String rawResponse
    ) {}
}
