package bolsilloApp.api.shared.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * En prod, una variable ausente ya rompe el arranque (los placeholders no tienen default).
 * Esto cubre ademas variables definidas pero vacias o con el placeholder de desarrollo.
 * El JWT secret se valida en {@link JwtService} para todos los perfiles.
 */
@Component
@Profile("prod")
public class ProdSecretsValidator implements InitializingBean {

    private final String anthropicApiKey;
    private final String datasourceUrl;
    private final String datasourcePassword;

    public ProdSecretsValidator(
            @Value("${anthropic.api-key}") String anthropicApiKey,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.password}") String datasourcePassword) {
        this.anthropicApiKey = anthropicApiKey;
        this.datasourceUrl = datasourceUrl;
        this.datasourcePassword = datasourcePassword;
    }

    @Override
    public void afterPropertiesSet() {
        require(datasourceUrl, "DATABASE_URL");
        require(datasourcePassword, "DATABASE_PASSWORD");
        require(anthropicApiKey, "ANTHROPIC_API_KEY");
        if (anthropicApiKey.contains("placeholder")) {
            throw new IllegalStateException("ANTHROPIC_API_KEY tiene un valor placeholder en prod");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " es obligatoria en el perfil prod");
        }
    }
}
