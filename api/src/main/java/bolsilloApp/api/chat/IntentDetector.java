package bolsilloApp.api.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule-based intent detection (no AI). Picks the probable intent, which decides
 * the prompt and the model used. Order: CORRECT → QUERY → REGISTER → CHAT.
 */
final class IntentDetector {

    private IntentDetector() {}

    private static final List<String> CORRECT_MARKERS = List.of(
            "no era", "eso era", "era de", "correg", "en realidad", "quise decir",
            "me equivoque", "no fue", "cambialo", "cambiala", "cambia la categoria");

    private static final List<String> QUERY_PREFIXES = List.of(
            "cuanto", "cuanta", "que ", "como ", "donde ", "por que", "cual", "cuando ",
            "dime", "decime", "mostrame", "resumen");

    /** A number that can be an amount: 500, 1.500, 1500,50, 2k, 3 mil, 10 lucas. */
    private static final String AMOUNT = "\\d[\\d.,]*\\s*(k|mil|lucas|luca)?";

    private static final Pattern HAS_AMOUNT = Pattern.compile(".*\\d.*");

    /** Currency markers near an amount: $500, 500 pesos, usd 20, 20 dolares... */
    private static final Pattern CURRENCY = Pattern.compile(
            "(\\$|\\b(ars|usd|us\\$|u\\$s|eur|euros?|pesos?|dolares?|dolar|lucas?|mangos?)\\b"
                    + "|\\d\\s*(k|mil)\\b)");

    /** Verbs/nouns that signal a movement of money (accents already stripped). */
    private static final Pattern MONEY_VERB = Pattern.compile(
            "\\b(gast\\w*|pag\\w*|compr\\w*|cobr\\w*|recib\\w*|gane|ganamos|ganaste|transferi\\w*"
                    + "|deposit\\w*|cargue|invert\\w*|invirti\\w*|abone|me (pagaron|depositaron|transfirieron)"
                    + "|sueldo|aguinaldo|factura|cuota|ingreso|egreso|gasto)\\b");

    /** Shorthand like "500 en uber", "1200 de nafta" (amount first, then a preposition). */
    private static final Pattern SHORTHAND = Pattern.compile(
            "^" + AMOUNT + "\\s+(en|de|por|para|al|del)\\s+[a-z].*");

    static ChatIntent detect(String message) {
        String text = normalize(message);

        if (CORRECT_MARKERS.stream().anyMatch(text::contains)) {
            return ChatIntent.CORRECT;
        }

        if (text.startsWith("¿") || text.contains("?")
                || QUERY_PREFIXES.stream().anyMatch(text::startsWith)) {
            return ChatIntent.QUERY;
        }

        if (HAS_AMOUNT.matcher(text).matches()
                && (CURRENCY.matcher(text).find()
                    || MONEY_VERB.matcher(text).find()
                    || SHORTHAND.matcher(text).matches())) {
            return ChatIntent.REGISTER;
        }

        return ChatIntent.CHAT;
    }

    static String normalize(String message) {
        String lower = message.toLowerCase().trim();
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
