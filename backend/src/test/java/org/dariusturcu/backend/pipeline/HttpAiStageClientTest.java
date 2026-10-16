package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiStageClientTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String BASE_URL = "http://ai.test";
    private static final String INTERNAL_KEY = "stage-test-internal-key";
    private static final Duration RETRY = Duration.ofSeconds(45);

    @Test
    void stageCallsCarryTheInternalKeyAndReadJsonResults() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL).defaultHeader("X-Internal-Api-Key", INTERNAL_KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/stages/musicbrainz")).andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("{\"candidates\":[],\"earliestYear\":null}", MediaType.APPLICATION_JSON));
        var client = new HttpAiStageClient(builder.build(), MAPPER);

        assertThat(client.execute(PipelineStage.MUSICBRAINZ, MAPPER.createObjectNode()).has("earliestYear")).isTrue();
        server.verify();
    }

    @Test
    void providerErrorsKeepTheirClassificationWithoutStoringProviderMessages() {
        var error = MAPPER.readTree("""
                {"providerHint":"API_KEY_PROVIDER","cause":"HTTP_ERROR","upstreamStatus":401,
                 "message":"https://provider.test/?key=secret"}
                """);
        AiStageFailure failure = HttpAiStageClient.mapFailure(HttpStatus.BAD_GATEWAY.value(), error);
        assertThat(failure.classification()).isEqualTo(FailureClassifier.KEY_REJECTED);
        assertThat(failure.getMessage()).doesNotContain("secret", "provider.test");
        assertThat(failure.retryAfter()).isNull();
    }

    @Test
    void providerRateLimitsAndLocalPaceDeferralsAreDifferent() {
        var deferred = HttpAiStageClient.mapFailure(HttpStatus.TOO_MANY_REQUESTS.value(),
                MAPPER.readTree("{\"retryAfterSeconds\":45}"));
        var providerLimited = HttpAiStageClient.mapFailure(HttpStatus.BAD_GATEWAY.value(), MAPPER.readTree("""
                {"providerHint":"KEYLESS_PROVIDER","cause":"HTTP_ERROR","upstreamStatus":429}
                """));
        assertThat(deferred.retryAfter()).isEqualTo(RETRY);
        assertThat(providerLimited.retryAfter()).isNull();
        assertThat(providerLimited.classification()).isEqualTo(FailureClassifier.TRANSIENT);
    }

    @Test
    void missingVideosArePermanentAndTimeoutsAreTransient() {
        assertThat(HttpAiStageClient.mapFailure(HttpStatus.UNPROCESSABLE_CONTENT.value(), MAPPER.readTree("""
                {"providerHint":"YOUTUBE_VIDEO_LOOKUP","cause":"NO_USABLE_METADATA","upstreamStatus":null}
                """)).classification()).isEqualTo(FailureClassifier.PERMANENT);
        assertThat(HttpAiStageClient.mapFailure(HttpStatus.GATEWAY_TIMEOUT.value(), MAPPER.readTree("""
                {"providerHint":"KEYLESS_PROVIDER","cause":"TIMEOUT","upstreamStatus":null}
                """)).classification()).isEqualTo(FailureClassifier.TRANSIENT);
    }

    @Test
    void malformedHttpResponsesAreRetryable() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/stages/musicbrainz"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("maintenance"));
        var client = new HttpAiStageClient(builder.build(), MAPPER);
        assertThatThrownBy(() -> client.execute(PipelineStage.MUSICBRAINZ, MAPPER.createObjectNode()))
                .isInstanceOf(AiStageFailure.class).hasMessage(HttpAiStageClient.UNKNOWN_FAILURE);
        server.verify();
    }

    @Test
    void internalAuthenticationFailurePausesTheStageEvenWithoutAStageErrorBody() {
        assertThat(HttpAiStageClient.mapFailure(HttpStatus.UNAUTHORIZED.value(),
                MAPPER.readTree("{\"detail\":\"Unauthorized\"}")).classification())
                .isEqualTo(FailureClassifier.KEY_REJECTED);
    }
}
