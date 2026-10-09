package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.client.RestClient;
import org.dariusturcu.backend.model.ai.VideoDurationsRequest;
import org.dariusturcu.backend.model.ai.VideoDurationsResponse;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class SongDurationRefreshService {
    public static final Duration RETENTION = Duration.ofDays(29);
    private static final String REFRESH_INTERVAL = "PT6H";
    private static final String VIDEO_DURATIONS_PATH = "/metadata/video-durations";
    private static final int VIDEO_BATCH_SIZE = 50;
    private static final int FIRST_PAGE = 0;
    private final SongRepository songRepository;
    private final RestClient aiServiceRestClient;

    @Scheduled(fixedDelayString = REFRESH_INTERVAL)
    public void refreshExpiredDurations() {
        Instant cutoff = Instant.now().minus(RETENTION);
        try {
            List<String> videoIds;
            while (!(videoIds = songRepository.findDurationRefreshCandidates(cutoff, PageRequest.of(FIRST_PAGE, VIDEO_BATCH_SIZE))).isEmpty()) {
                VideoDurationsResponse response = aiServiceRestClient.post().uri(VIDEO_DURATIONS_PATH)
                        .body(new VideoDurationsRequest(videoIds)).retrieve().body(VideoDurationsResponse.class);
                if (response == null || response.durations() == null || !response.durations().keySet().containsAll(videoIds)) {
                    throw new IllegalStateException("Incomplete YouTube duration response");
                }
                Instant checkedAt = Instant.now();
                for (String videoId : videoIds) {
                    Integer duration = response.durations().get(videoId);
                    songRepository.updateRefreshedDuration(videoId, duration != null && duration > 0 ? duration : null, checkedAt, cutoff);
                }
            }
        } catch (RuntimeException refreshError) {
            log.warn("YouTube duration refresh failed; due videos remain eligible for the next sweep", refreshError);
        } finally {
            songRepository.clearExpiredDurations(cutoff);
        }
    }
}
