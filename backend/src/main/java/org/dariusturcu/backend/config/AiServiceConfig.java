package org.dariusturcu.backend.config;

import org.dariusturcu.backend.observability.CorrelationIdPropagatingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class AiServiceConfig {

    @Bean
    public RestClient aiServiceRestClient(
            @Value("${ai.service.base-url}") String baseUrl,
            @Value("${ai.service.internal-api-key}") String internalApiKey,
            CorrelationIdPropagatingInterceptor correlationIdPropagatingInterceptor
    ) {
        // RestClient's default JdkClientHttpRequestFactory prefers HTTP/2 and attempts
        // a cleartext (h2c) upgrade on every plain-HTTP request. The AI microservice's
        // ASGI server only speaks HTTP/1.1 and rejects that upgrade outright, so every
        // call here failed before FastAPI ever saw the request. Pinning the client to
        // HTTP/1.1 is what makes a metadata request actually go through.
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestInterceptor(correlationIdPropagatingInterceptor)
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
    }
}
