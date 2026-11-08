package bolsilloApp.api.shared.security;

import bolsilloApp.api.ApiApplication;
import bolsilloApp.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Levanta la app real con el perfil prod (sin defaults) contra la DB de Testcontainers.
 * Los valores se pasan como argumentos de linea de comando para que tengan prioridad
 * sobre cualquier variable de entorno o api/.env de la maquina.
 */
class ProdProfileStartupTest {

    private static final String VALID_SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void prodArrancaConTodasLasVariables() {
        try (ConfigurableApplicationContext ctx = start(Map.of())) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }

    @Test
    void prodConJwtSecretCortoNoArranca() {
        assertThatThrownBy(() -> start(Map.of("JWT_SECRET", "c2hvcnQtc2VjcmV0")).close())
                .rootCause().hasMessageContaining("256 bits");
    }

    @Test
    void prodConJwtSecretVacioNoArranca() {
        assertThatThrownBy(() -> start(Map.of("JWT_SECRET", "")).close())
                .rootCause().hasMessageContaining("JWT_SECRET");
    }

    @Test
    void prodConAnthropicKeyVaciaNoArranca() {
        assertThatThrownBy(() -> start(Map.of("ANTHROPIC_API_KEY", "")).close())
                .rootCause().hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void prodConAnthropicKeyPlaceholderNoArranca() {
        assertThatThrownBy(() -> start(Map.of("ANTHROPIC_API_KEY", "sk-ant-placeholder")).close())
                .rootCause().hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void prodSinJwtSecretDefinidoNoArranca() {
        // Solo verificable si la maquina no define JWT_SECRET por fuera (env o api/.env)
        assumeTrue(System.getenv("JWT_SECRET") == null);
        assertThatThrownBy(() -> start(Map.of("JWT_SECRET", OMIT)).close())
                .hasStackTraceContaining("JWT_SECRET");
    }

    private static final String OMIT = "\0omit";

    private ConfigurableApplicationContext start(Map<String, String> overrides) {
        var pg = AbstractIntegrationTest.postgres();
        Map<String, String> props = new LinkedHashMap<>();
        props.put("DATABASE_URL", pg.getJdbcUrl());
        props.put("DATABASE_USER", pg.getUsername());
        props.put("DATABASE_PASSWORD", pg.getPassword());
        props.put("JWT_SECRET", VALID_SECRET);
        props.put("ANTHROPIC_API_KEY", "sk-ant-test");
        props.put("server.port", "0");
        props.putAll(overrides);
        String[] args = props.entrySet().stream()
                .filter(e -> !OMIT.equals(e.getValue()))
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(ApiApplication.class)
                .profiles("prod")
                .run(args);
    }
}
