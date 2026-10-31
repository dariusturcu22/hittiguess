package org.dariusturcu.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicHealthControllerTest {

    @Test
    void healthReturnsOkWhenAllIndicatorsAreUp() {
        HealthIndicator databaseHealthIndicator = mock(HealthIndicator.class);
        HealthIndicator aiServiceHealthIndicator = mock(HealthIndicator.class);

        when(databaseHealthIndicator.health()).thenReturn(Health.status(Status.UP).build());
        when(aiServiceHealthIndicator.health()).thenReturn(Health.status(Status.UP).build());

        PublicHealthController controller = new PublicHealthController(
                List.of(databaseHealthIndicator, aiServiceHealthIndicator)
        );

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("groups")).isEqualTo(List.of("liveness", "readiness"));
    }

    @Test
    void healthReturnsServiceUnavailableWhenAnyIndicatorIsDown() {
        HealthIndicator databaseHealthIndicator = mock(HealthIndicator.class);
        HealthIndicator aiServiceHealthIndicator = mock(HealthIndicator.class);

        when(databaseHealthIndicator.health()).thenReturn(Health.status(Status.UP).build());
        when(aiServiceHealthIndicator.health()).thenReturn(Health.status(Status.DOWN).build());

        PublicHealthController controller = new PublicHealthController(
                List.of(databaseHealthIndicator, aiServiceHealthIndicator)
        );

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("DOWN");
    }
}
