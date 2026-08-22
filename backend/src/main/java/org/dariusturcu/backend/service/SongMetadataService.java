package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.AiServiceResolveResponse;
import org.dariusturcu.backend.model.ai.MetadataResolveRequest;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongMetadataService {
    private final RestClient aiServiceRestClient;

    // The metadata pipeline chains several rate-limited external calls plus a paid LLM call,
    // so one user queuing many concurrent requests can tie up threads and run up cost. Capping
    // it at one in-flight request per user, rather than a time window, matches the actual risk.
    private final Set<Long> usersWithRequestInFlight = ConcurrentHashMap.newKeySet();

    public AiResponse fetchMetadata(String youtubeUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!usersWithRequestInFlight.add(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A metadata request is already in progress");
        }

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
        } catch (Exception e) {
            log.warn("AI microservice call failed: {}", e.getMessage());
            return new AiResponse(null, null, System.currentTimeMillis() - startTime, LocalDateTime.now(), "ERROR");
        } finally {
            usersWithRequestInFlight.remove(userId);
        }
    }
}
