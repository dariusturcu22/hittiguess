package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.AiServiceResolveResponse;
import org.dariusturcu.backend.model.ai.MetadataResolveRequest;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongMetadataService {
    private static final String YOUTUBE_WATCH_URL_PREFIX = "https://www.youtube.com/watch?v=";

    private final RestClient aiServiceRestClient;

    // The metadata pipeline chains several rate-limited external calls plus a paid LLM call,
    // so one user queuing many concurrent requests can tie up threads and run up cost. Capping
    // it at one in-flight request per user, rather than a time window, matches the actual risk.
    private final Set<Long> usersWithRequestInFlight = ConcurrentHashMap.newKeySet();

    public AiResponse fetchMetadata(String youtubeUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!usersWithRequestInFlight.add(userId)) {
            throw new RateLimitExceededException("A metadata request is already in progress");
        }

        try {
            return resolve(youtubeUrl);
        } finally {
            usersWithRequestInFlight.remove(userId);
        }
    }

    // The per-user in-flight gate above is request-scoped and has no meaning for the scheduled
    // backlog drain, which runs with no authenticated user, so resolution by raw ID skips it.
    // The priority coordinator, not a per-user cap, is what paces the drain against on-the-spot
    // traffic for the shared external rate-limit budget.
    public AiResponse resolveByYoutubeId(String youtubeId) {
        return resolve(YOUTUBE_WATCH_URL_PREFIX + youtubeId);
    }

    private AiResponse resolve(String youtubeUrl) {
        long startTime = System.currentTimeMillis();

        try {
            AiServiceResolveResponse response = aiServiceRestClient.post()
                    .uri("/metadata/resolve")
                    .body(new MetadataResolveRequest(youtubeUrl))
                    .retrieve()
                    .body(AiServiceResolveResponse.class);

            long duration = System.currentTimeMillis() - startTime;

            if (response == null || !"SUCCESS".equals(response.status())) {
                return new AiResponse(null, response != null ? response.model() : null, duration, LocalDateTime.now(), "ERROR");
            }

            return new AiResponse(response.content(), response.model(), duration, LocalDateTime.now(), "SUCCESS");
        } catch (Exception aiServiceCallFailure) {
            log.warn("AI microservice call failed: {}", aiServiceCallFailure.getMessage());
            return new AiResponse(null, null, System.currentTimeMillis() - startTime, LocalDateTime.now(), "ERROR");
        }
    }
}
