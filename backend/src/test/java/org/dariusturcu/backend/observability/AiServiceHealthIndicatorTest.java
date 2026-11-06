package org.dariusturcu.backend.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceHealthIndicatorTest {

    @Mock
    private RestClient aiServiceRestClient;

    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private RestClient.ResponseSpec responseSpec;
    private AiServiceHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        healthIndicator = new AiServiceHealthIndicator(aiServiceRestClient);
    }

    @Test
    void reportsUpWhenAiServiceAnswersHealthEndpoint() {
        when(aiServiceRestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/health")).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        Health reportedHealth = healthIndicator.health();

        assertThat(reportedHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(reportedHealth.getDetails()).containsEntry("reachability", "connected");
    }

    @Test
    void reportsDownWhenAiServiceThrowsException() {
        when(aiServiceRestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

        Health reportedHealth = healthIndicator.health();

        assertThat(reportedHealth.getStatus()).isEqualTo(Status.DOWN);
        assertThat(reportedHealth.getDetails()).containsEntry("reachability", "unreachable");
    }
}
