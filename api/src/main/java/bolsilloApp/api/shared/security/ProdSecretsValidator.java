package bolsilloApp.api.shared.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * En prod, una variable ausente ya rompe el arranque (los placeholders no tienen default).
 * Esto cubre ademas variables definidas pero vacias o con el placeholder de desarrollo.
 * El formato del JWT secret se valida en {@link JwtService} para todos los perfiles; aca se rechaza
 * ademas que en prod se use alguno de los secrets de desarrollo que viven en el repo publico.
 */
@Component
@Profile("prod")
public class ProdSecretsValidator implements InitializingBean {

    private final String anthropicApiKey;
    private final String datasourceUrl;
    private final String datasourcePassword;
    private final String jwtSecret;

    // Secrets de desarrollo publicados en el repo (docker-compose.yml y application-dev.properties).
    static final java.util.Set<String> DEV_JWT_SECRETS = java.util.Set.of(
            "hH4d9Xa3M5mskNX0MCXF1+OkKUFPg3nllFdSydPyXv0=",
            "j+ERL/9Om19ZbERIadbopvgahYXwMAbYNo2bOkcGym4=");

    public ProdSecretsValidator(
            @Value("${anthropic.api-key}") String anthropicApiKey,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${app.jwt.secret}") String jwtSecret) {
        this.anthropicApiKey = anthropicApiKey;
        this.datasourceUrl = datasourceUrl;
        this.datasourcePassword = datasourcePassword;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void afterPropertiesSet() {
        require(datasourceUrl, "DATABASE_URL");
        require(datasourcePassword, "DATABASE_PASSWORD");
        require(anthropicApiKey, "ANTHROPIC_API_KEY");
        if (anthropicApiKey.contains("placeholder")) {
            throw new IllegalStateException("ANTHROPIC_API_KEY tiene un valor placeholder en prod");
        }
        if (jwtSecret != null && DEV_JWT_SECRETS.contains(jwtSecret.trim())) {
            throw new IllegalStateException("JWT_SECRET es un secret de desarrollo del repo; genera uno propio (openssl rand -base64 32)");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " es obligatoria en el perfil prod");
        }
    }
}
