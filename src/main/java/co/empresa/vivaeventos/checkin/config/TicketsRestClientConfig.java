package co.empresa.vivaeventos.checkin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class TicketsRestClientConfig {

    @Bean
    public RestClient ticketsRestClient(
            @Value("${tickets.service.url}") String baseUrl,
            @Value("${tickets.service.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${tickets.service.read-timeout-ms:5000}") long readTimeoutMs) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
