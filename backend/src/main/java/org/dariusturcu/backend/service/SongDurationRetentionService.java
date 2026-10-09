package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SongDurationRetentionService {
    public static final Duration RETENTION = Duration.ofDays(29);
    private static final String CLEANUP_INTERVAL = "PT1H";
    private final SongRepository songRepository;

    @Scheduled(fixedDelayString = CLEANUP_INTERVAL)
    @Transactional
    public void clearExpiredDurations() {
        songRepository.clearExpiredDurations(Instant.now().minus(RETENTION));
    }
}
