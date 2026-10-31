package org.dariusturcu.backend.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "management.server.port")
public class PublicHealthController {

    private final List<HealthIndicator> healthIndicators;

    public PublicHealthController(List<HealthIndicator> healthIndicators) {
        this.healthIndicators = healthIndicators;
    }

    @GetMapping({"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/health"})
    public ResponseEntity<Map<String, Object>> health() {
        boolean allHealthy = healthIndicators.stream()
                .map(HealthIndicator::health)
                .allMatch(health -> Status.UP.equals(health.getStatus()));

        String statusText = allHealthy ? "UP" : "DOWN";
        Map<String, Object> responseBody = Map.of(
                "status", statusText,
                "groups", List.of("liveness", "readiness")
        );

        return allHealthy
                ? ResponseEntity.ok(responseBody)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(responseBody);
    }
}
