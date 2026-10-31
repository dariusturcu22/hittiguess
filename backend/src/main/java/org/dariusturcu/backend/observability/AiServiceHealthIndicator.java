package org.dariusturcu.backend.observability;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component("aiService")
public class AiServiceHealthIndicator implements HealthIndicator {

    private final RestClient aiServiceRestClient;

    public AiServiceHealthIndicator(@Qualifier("aiServiceRestClient") RestClient aiServiceRestClient) {
        this.aiServiceRestClient = aiServiceRestClient;
    }

    @Override
    public Health health() {
        try {
            aiServiceRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return Health.up().withDetail("reachability", "connected").build();
        } catch (Exception reachabilityFailure) {
            return Health.down()
                    .withDetail("reachability", "unreachable")
                    .withDetail("error", reachabilityFailure.getMessage())
                    .build();
        }
    }
}
