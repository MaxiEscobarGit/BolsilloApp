package bolsilloApp.api.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI con esquema Bearer JWT, para poder probar los
 * endpoints protegidos desde Swagger UI (botón "Authorize").
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI bolsilloOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BolsilloApp API")
                        .version("v1")
                        .description("API de finanzas personales con asistente AI."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
