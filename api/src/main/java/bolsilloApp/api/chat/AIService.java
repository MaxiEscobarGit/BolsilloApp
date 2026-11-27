package bolsilloApp.api.chat;

import bolsilloApp.api.chat.dto.AiParsedResponse;
import bolsilloApp.api.chat.dto.AiPromptContext;
import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Única clase que llama a la IA (Anthropic por defecto, Gemini si {@code app.ai.provider=gemini}).
 * No debe ejecutarse dentro de una transacción de BD.
 */
@Service
@Slf4j
public class AIService {

    private static final int MAX_TOKENS = 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // Gemini cuenta los tokens de "thinking" dentro de maxOutputTokens: se deja margen.
    private static final int GEMINI_MAX_TOKENS = 4096;

    private final WebClient anthropicWebClient;
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper;
    private final boolean gemini;
    private final String fastModel;
    private final String smartModel;

    public AIService(@Qualifier("anthropicWebClient") WebClient anthropicWebClient,
                     @Qualifier("geminiWebClient") WebClient geminiWebClient,
                     ObjectMapper objectMapper,
                     @Value("${app.ai.provider:anthropic}") String provider,
                     @Value("${anthropic.model.fast:claude-haiku-4-5}") String anthropicFast,
                     @Value("${anthropic.model.smart:claude-sonnet-5-5}") String anthropicSmart,
                     @Value("${gemini.model.fast:gemini-3.5-flash-lite}") String geminiFast,
                     @Value("${gemini.model.smart:gemini-3.8-flash}") String geminiSmart) {
        this.anthropicWebClient = anthropicWebClient;
        this.geminiWebClient = geminiWebClient;
        this.objectMapper = objectMapper;
        String p = provider == null ? "anthropic" : provider.trim().toLowerCase();
        if (!p.equals("anthropic") && !p.equals("gemini")) {
            throw new IllegalArgumentException("app.ai.provider invalido: '" + provider + "' (anthropic | gemini)");
        }
        this.gemini = p.equals("gemini");
        this.fastModel = gemini ? geminiFast : anthropicFast;
        this.smartModel = gemini ? geminiSmart : anthropicSmart;
    }

    public AiResponse call(String userMessage, ChatIntent intent, AiPromptContext context) {
        String model = modelFor(intent);
        String systemPrompt = buildSystemPrompt(intent, context);

        String responseJson;
        try {
            responseJson = sendMessage(model, systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("AI API call failed (model={})", model, e);
            throw new ApiException("AI service temporarily unavailable", "AI_UNAVAILABLE", 503);
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String textContent = extractText(root);
            if (textContent == null) {
                throw new IllegalStateException("No text content in AI response");
            }
            int tokensUsed = tokensUsed(root);

            AiParsedResponse parsed = tryParseStructured(textContent, intent);

            return new AiResponse(
                    parsed != null ? parsed.message() : textContent,
                    parsed != null ? parsed.intent() : intent,
                    parsed != null ? parsed.transaction() : null,
                    model,
                    tokensUsed,
                    textContent
            );
        } catch (Exception e) {
            log.error("Invalid response from AI (model={})", model, e);
            throw new ApiException("AI service temporarily unavailable", "AI_UNAVAILABLE", 503);
        }
    }

    /** Free-text answer from the smart model (no JSON contract), for one-off texts like the monthly summary. */
    public String complete(String systemPrompt, String userMessage) {
        try {
            String text = extractText(objectMapper.readTree(sendMessage(smartModel, systemPrompt, userMessage, false)));
            if (text == null || text.isBlank()) throw new IllegalStateException("No text content in AI response");
            return text.trim();
        } catch (Exception e) {
            log.error("AI API call failed (model={})", smartModel, e);
            throw new ApiException("AI service temporarily unavailable", "AI_UNAVAILABLE", 503);
        }
    }

    String modelFor(ChatIntent intent) {
        return intent == ChatIntent.QUERY ? smartModel : fastModel;
    }

    /**
     * Raw HTTP call to the configured provider, asking for a JSON answer.
     * Returns the response body as JSON text. Package-visible so tests can stub it.
     */
    String sendMessage(String model, String systemPrompt, String userMessage) {
        return sendMessage(model, systemPrompt, userMessage, true);
    }

    /** {@code json=false} asks for free text (only matters for Gemini, which has a JSON response mode). */
    String sendMessage(String model, String systemPrompt, String userMessage, boolean json) {
        return gemini
                ? geminiWebClient.post()
                        .uri("/v1beta/models/{model}:generateContent", model)
                        .bodyValue(geminiRequest(systemPrompt, userMessage, json))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(TIMEOUT)
                        .block()
                : anthropicWebClient.post()
                        .uri("/v1/messages")
                        .bodyValue(anthropicRequest(model, systemPrompt, userMessage))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(TIMEOUT)
                        .block();
    }

    Map<String, Object> anthropicRequest(String model, String systemPrompt, String userMessage) {
        return Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );
    }

    Map<String, Object> geminiRequest(String systemPrompt, String userMessage, boolean json) {
        Map<String, Object> generationConfig = new java.util.LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", GEMINI_MAX_TOKENS);
        if (json) {
            generationConfig.put("responseMimeType", "application/json");
        }
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userMessage)))),
                "generationConfig", generationConfig
        );
    }

    private String extractText(JsonNode root) {
        if (gemini) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.hasNonNull("text")) {
                    sb.append(part.path("text").asString());
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asString(""))) {
                return block.path("text").asString();
            }
        }
        return null;
    }

    private int tokensUsed(JsonNode root) {
        if (gemini) {
            return root.path("usageMetadata").path("totalTokenCount").asInt();
        }
        return root.path("usage").path("input_tokens").asInt()
                + root.path("usage").path("output_tokens").asInt();
    }

    private AiParsedResponse tryParseStructured(String text, ChatIntent intent) {
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));

            String message = textOrNull(node, "message");
            if (message == null) {
                message = text;
            }

            ChatIntent parsedIntent = intent;
            String intentText = textOrNull(node, "intent");
            if (intentText != null) {
                try {
                    parsedIntent = ChatIntent.valueOf(intentText.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // keep the detected intent
                }
            }

            AiParsedResponse.TransactionData txData = null;
            JsonNode tx = node.get("transaction");
            if (tx != null && tx.isObject()) {
                String amount = textOrNull(tx, "amount");
                String date = textOrNull(tx, "date");
                txData = new AiParsedResponse.TransactionData(
                        amount != null ? new BigDecimal(amount.replace(",", ".")) : null,
                        textOrNull(tx, "categoryName"),
                        textOrNull(tx, "description"),
                        textOrNull(tx, "type"),
                        date != null ? LocalDate.parse(date) : null,
                        textOrNull(tx, "currency"),
                        textOrNull(tx, "alias")
                );
            }

            return new AiParsedResponse(message, parsedIntent, txData);
        } catch (Exception e) {
            log.debug("Could not parse structured response from AI: {}", e.getMessage());
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = value.asString().trim();
        return s.isEmpty() ? null : s;
    }

    String buildSystemPrompt(ChatIntent intent, AiPromptContext context) {
        MetricsContext metrics = context.metrics();
        StringBuilder sb = new StringBuilder();
        sb.append("Eres un asistente financiero personal. Responde en espanol.\n");
        sb.append("Fecha de hoy: ").append(context.today()).append("\n\n");

        sb.append("CONTEXTO FINANCIERO DEL USUARIO (mes actual, montos en ARS):\n");
        sb.append("- Ingresos totales: $").append(metrics.totalIncomeCents() / 100.0).append("\n");
        sb.append("- Gastos totales: $").append(metrics.totalExpenseCents() / 100.0).append("\n");
        sb.append("- Balance: $").append(metrics.balanceCents() / 100.0).append("\n");

        if (metrics.expenseByCategory() != null && !metrics.expenseByCategory().isEmpty()) {
            sb.append("- Gastos por categoria: ");
            metrics.expenseByCategory().forEach((cat, amount) ->
                    sb.append(cat).append("=$").append(amount / 100.0).append(" "));
            sb.append("\n");
        }

        if (metrics.activeDebts() != null && !metrics.activeDebts().isEmpty()) {
            sb.append("- Deudas activas: ");
            metrics.activeDebts().forEach(d ->
                    sb.append(d.counterpartName()).append(" (").append(d.direction()).append(") $")
                            .append(d.amountCents() / 100.0).append(" "));
            sb.append("\n");
        }

        if (!context.aliases().isEmpty()) {
            sb.append("\nALIAS DEL USUARIO (nombres que usa para comercios/categorias):\n");
            context.aliases().forEach(a -> sb.append("- \"").append(a.get("alias")).append("\" -> ")
                    .append(a.get("category")).append("\n"));
        }

        sb.append("\nCATEGORIAS DISPONIBLES (usa exactamente uno de estos nombres):\n");
        sb.append("- Gastos (expense): ").append(String.join(", ", context.expenseCategories())).append("\n");
        sb.append("- Ingresos (income): ").append(String.join(", ", context.incomeCategories())).append("\n");

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
                        "currency": "codigo ISO de la moneda (ARS si no especifica; USD si dice dolares/usd/u$s)",
                        "categoryName": "nombre exacto de una categoria disponible del tipo correcto",
                        "description": "descripcion breve",
                        "type": "expense o income",
                        "date": "YYYY-MM-DD (hoy si no especifica)"
                      }
                    }
                    Si falta el monto o no queda claro, responde con "transaction": null y pedi el dato faltante.
                    """);
            case QUERY -> sb.append("""
                    El usuario pregunta sobre sus finanzas. Usa el contexto proporcionado para responder.
                    Responde con JSON: {"message": "tu respuesta detallada", "intent": "QUERY", "transaction": null}
                    """);
            case CORRECT -> {
                TransactionResponse target = context.correctionTarget();
                if (target != null) {
                    sb.append("TRANSACCION A CORREGIR: ")
                            .append(target.type()).append(" de ")
                            .append(target.amount()).append(" ").append(target.currency())
                            .append(", categoria \"").append(target.categoryName()).append("\"")
                            .append(", descripcion \"").append(target.description()).append("\"")
                            .append(", fecha ").append(target.transactionDate()).append("\n");
                }
                sb.append("""
                        El usuario quiere corregir esa transaccion.
                        Responde UNICAMENTE con JSON valido (sin markdown), incluyendo SOLO los campos que cambian:
                        {
                          "message": "confirmacion de la correccion",
                          "intent": "CORRECT",
                          "transaction": {
                            "amount": 1234.56,
                            "currency": "ARS",
                            "categoryName": "categoria corregida",
                            "description": "descripcion corregida",
                            "type": "expense o income",
                            "date": "YYYY-MM-DD",
                            "alias": "termino que usa el usuario para el comercio o lugar (ej: \\"el chino\\"), o null"
                          }
                        }
                        """);
            }
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
