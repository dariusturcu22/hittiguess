package org.dariusturcu.backend.config;

import org.dariusturcu.backend.observability.CorrelationIdPropagatingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServiceConfig {

    @Bean
    public RestClient aiServiceRestClient(
            @Value("${ai.service.base-url}") String baseUrl,
            @Value("${ai.service.internal-api-key}") String internalApiKey,
            CorrelationIdPropagatingInterceptor correlationIdPropagatingInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestInterceptor(correlationIdPropagatingInterceptor)
                .build();
    }
}
