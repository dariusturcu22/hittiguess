package org.dariusturcu.backend.config;

import org.dariusturcu.backend.observability.CorrelationIdPropagatingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;

@Configuration
public class AiServiceConfig {
    private static final Duration INDEXING_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INDEXING_READ_TIMEOUT = Duration.ofSeconds(120);

    @Bean(defaultCandidate = false)
    public RestClient catalogIndexingRestClient(
            @Value("${ai.service.base-url}") String baseUrl,
            @Value("${ai.service.internal-api-key}") String internalApiKey,
            CorrelationIdPropagatingInterceptor correlationIdPropagatingInterceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(INDEXING_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(INDEXING_READ_TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestInterceptor(correlationIdPropagatingInterceptor)
                .requestFactory(requestFactory).build();
    }

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
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }
}
