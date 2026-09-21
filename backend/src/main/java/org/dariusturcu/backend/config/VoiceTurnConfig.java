package org.dariusturcu.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class VoiceTurnConfig {

    @Bean
    public RestClient cloudflareTurnRestClient(
            @Value("${voice.turn.cloudflare.api-token:}") String apiToken) {
        return RestClient.builder()
                .baseUrl("https://rtc.live.cloudflare.com")
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }
}
