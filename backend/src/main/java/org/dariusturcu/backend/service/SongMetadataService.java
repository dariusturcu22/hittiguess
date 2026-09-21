package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.ai.AiMetadataContent;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.AiServiceResolveResponse;
import org.dariusturcu.backend.model.ai.MetadataResolveRequest;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongMetadataService {
    private static final String YOUTUBE_WATCH_URL_PREFIX = "https://www.youtube.com/watch?v=";
    private static final Duration PREVIEW_CACHE_TTL = Duration.ofMinutes(10);

    private final RestClient aiServiceRestClient;

    // The metadata pipeline chains several rate-limited external calls plus a paid LLM call,
    // so one user queuing many concurrent requests can tie up threads and run up cost. Capping
    // it at one in-flight request per user, rather than a time window, matches the actual risk.
    private final Set<Long> usersWithRequestInFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<PreviewCacheKey, CachedPreview> previewMetadataByKey = new ConcurrentHashMap<>();

    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String REJECTED_STATUS = "REJECTED";
    private static final String ERROR_STATUS = "ERROR";

    public AiResponse fetchMetadata(String youtubeUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!usersWithRequestInFlight.add(userId)) {
            throw new RateLimitExceededException("A metadata request is already in progress");
        }

        try {
            AiResponse response = resolve(youtubeUrl);
            Optional<String> youtubeId = YoutubeLinkParser.parseVideoId(youtubeUrl);
            if (SUCCESS_STATUS.equals(response.status()) && response.content() != null && youtubeId.isPresent()) {
                previewMetadataByKey.put(new PreviewCacheKey(userId, youtubeId.get()), new CachedPreview(response, Instant.now()));
            }
            return response;
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

    public Optional<AiResponse> findCachedPreview(String youtubeId) {
        PreviewCacheKey cacheKey = new PreviewCacheKey(SecurityUtils.getCurrentUserId(), youtubeId);
        CachedPreview cachedPreview = previewMetadataByKey.get(cacheKey);
        if (cachedPreview == null) {
            return Optional.empty();
        }
        if (cachedPreview.createdAt().plus(PREVIEW_CACHE_TTL).isAfter(Instant.now())) {
            return Optional.of(cachedPreview.response());
        }
        previewMetadataByKey.remove(cacheKey, cachedPreview);
        return Optional.empty();
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

            if (response == null) {
                return new AiResponse(null, null, duration, LocalDateTime.now(), ERROR_STATUS, null, null);
            }

            if (REJECTED_STATUS.equals(response.status())) {
                return new AiResponse(null, response.model(), duration, LocalDateTime.now(),
                        REJECTED_STATUS, response.rejectionReason(), response.rejectionDetail());
            }

            if (!SUCCESS_STATUS.equals(response.status())) {
                return new AiResponse(null, response.model(), duration, LocalDateTime.now(), ERROR_STATUS, null, null);
            }

            return new AiResponse(toSongMetadataResponse(response.content()), response.model(), duration, LocalDateTime.now(), SUCCESS_STATUS, null, null);
        } catch (Exception aiServiceCallFailure) {
            log.warn("AI microservice call failed: {}", aiServiceCallFailure.getMessage());
            return new AiResponse(null, null, System.currentTimeMillis() - startTime, LocalDateTime.now(), ERROR_STATUS, null, null);
        }
    }

    // AiMetadataContent mirrors the AI microservice's snake_case wire format on
    // the way in; SongMetadataResponse is the outbound shape the frontend
    // actually consumes (camelCase, matching every other DTO in the API). The
    // two must stay distinct types rather than one dual-purpose record, since
    // a single Jackson naming strategy can't be snake_case for one direction
    // and camelCase for the other.
    private static SongMetadataResponse toSongMetadataResponse(AiMetadataContent content) {
        if (content == null) {
            return null;
        }
        return new SongMetadataResponse(
                content.title(),
                content.artist(),
                content.releaseYear(),
                content.color(),
                content.confidence(),
                content.source(),
                content.reasoning(),
                content.verificationStatus(),
                content.sitelinksCount()
        );
    }

    private record PreviewCacheKey(Long userId, String youtubeId) {
    }

    private record CachedPreview(AiResponse response, Instant createdAt) {
    }
}
