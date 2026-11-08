package bolsilloApp.api.exchangerates.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ExchangeRateProperties.class)
public class ExchangeRatesConfig {

    public static final String WEB_CLIENT = "exchangeRatesWebClient";

    /**
     * WebClient propio para la API de cotizaciones. {@code defaultCandidate = false} evita que
     * compita con el WebClient de Anthropic en inyecciones por tipo: solo se inyecta con
     * {@code @Qualifier(ExchangeRatesConfig.WEB_CLIENT)}.
     */
    @Bean(name = WEB_CLIENT, defaultCandidate = false)
    @Qualifier(WEB_CLIENT)
    public WebClient exchangeRatesWebClient(ExchangeRateProperties properties) {
        int timeoutMs = (int) properties.timeout().toMillis();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .responseTimeout(properties.timeout());
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
