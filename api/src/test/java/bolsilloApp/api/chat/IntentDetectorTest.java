package bolsilloApp.api.chat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDetectorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Gasté 5000 en el super                 | REGISTER",
            "pagué la luz 12.500                    | REGISTER",
            "$1500 nafta                            | REGISTER",
            "20 usd en amazon                       | REGISTER",
            "me pagaron el sueldo 850000            | REGISTER",
            "500 en uber                            | REGISTER",
            "2k de verduleria                       | REGISTER",
            "cobré 30 lucas de un laburo            | REGISTER",
            "tengo 3 hijos                          | CHAT",
            "quiero ahorrar para 2027               | CHAT",
            "hola!                                  | CHAT",
            "¿Cuánto gasté este mes?                | QUERY",
            "cuanto llevo en comida                 | QUERY",
            "qué categoría gasté más en 2026?       | QUERY",
            "no era comida, era supermercado        | CORRECT",
            "eso era del chino                      | CORRECT",
            "me equivoqué, eran 6000                | CORRECT",
    })
    void detectaIntencion(String message, ChatIntent expected) {
        assertThat(IntentDetector.detect(message.trim())).isEqualTo(expected);
    }
}
