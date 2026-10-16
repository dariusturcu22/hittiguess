package org.dariusturcu.backend.pipeline;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "pipeline.stages.enabled", havingValue = "true")
public class HttpAiStageClient implements AiStageClient {
    private enum Provider { YOUTUBE, MUSICBRAINZ, DISCOGS, WIKIDATA, WIKIPEDIA, DEEPINFRA, OPENAI }
    static final Map<PipelineStage, String> PATHS = Map.of(
            PipelineStage.YOUTUBE_METADATA, "/stages/youtube-metadata",
            PipelineStage.MUSICBRAINZ, "/stages/musicbrainz",
            PipelineStage.DISCOGS, "/stages/discogs",
            PipelineStage.WIKIDATA, "/stages/wikidata",
            PipelineStage.WIKIPEDIA, "/stages/wikipedia",
            PipelineStage.LLM_RECONCILIATION, "/stages/llm-reconcile");
    static final String UNKNOWN_FAILURE = "AI stage returned an invalid response";
    static final int PACE_DEFERRED_STATUS = 429;
    static final long MINIMUM_RETRY_SECONDS = 1;

    private final RestClient client;
    private final ObjectMapper mapper;

    public HttpAiStageClient(@Qualifier("pipelineStageRestClient") RestClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public JsonNode execute(PipelineStage stage, JsonNode request) {
        String path = PATHS.get(stage);
        if (path == null) {
            throw new IllegalArgumentException("Stage has no AI endpoint: " + stage);
        }
        try {
            return client.post().uri(path).body(request).exchange((httpRequest, response) -> {
                JsonNode body;
                try {
                    body = mapper.readTree(response.getBody());
                } catch (RuntimeException invalidResponse) {
                    throw new AiStageFailure(UNKNOWN_FAILURE, FailureClassifier.TRANSIENT, null);
                }
                if (response.getStatusCode().is2xxSuccessful() && body != null && body.isObject()) {
                    return body;
                }
                throw mapFailure(response.getStatusCode().value(), body);
            });
        } catch (ResourceAccessException unreachable) {
            throw new AiStageFailure("AI stage could not be reached", FailureClassifier.TRANSIENT, null);
        }
    }

    static AiStageFailure mapFailure(int status, JsonNode body) {
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new AiStageFailure("Internal AI key rejected", FailureClassifier.KEY_REJECTED, null);
        }
        if (body == null || !body.isObject()) {
            return new AiStageFailure(UNKNOWN_FAILURE, FailureClassifier.TRANSIENT, null);
        }
        if (status == PACE_DEFERRED_STATUS && body.path("retryAfterSeconds").isNumber()) {
            long seconds = Math.max(MINIMUM_RETRY_SECONDS, body.path("retryAfterSeconds").asLong());
            return new AiStageFailure("Source pace deferred", FailureClassifier.TRANSIENT, Duration.ofSeconds(seconds));
        }
        try {
            FailureClassifier.FailureCause cause = FailureClassifier.FailureCause.valueOf(body.path("cause").asText());
            FailureClassifier.ProviderHint provider = FailureClassifier.ProviderHint.valueOf(body.path("providerHint").asText());
            Integer upstreamStatus = body.path("upstreamStatus").isNumber() ? body.path("upstreamStatus").asInt() : null;
            String reason = providerName(body) + " " + cause.name() + (upstreamStatus == null ? "" : " (HTTP " + upstreamStatus + ")");
            return new AiStageFailure(reason, FailureClassifier.classify(upstreamStatus, cause, provider), null);
        } catch (RuntimeException unknownContract) {
            return new AiStageFailure(UNKNOWN_FAILURE, FailureClassifier.TRANSIENT, null);
        }
    }

    private static String providerName(JsonNode body) {
        try { return Provider.valueOf(body.path("provider").asText()).name(); }
        catch (RuntimeException unknownProvider) { return "Provider"; }
    }
}
