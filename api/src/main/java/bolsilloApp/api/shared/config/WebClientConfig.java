package bolsilloApp.api.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Bean
    public WebClient anthropicWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", anthropicApiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }
}
