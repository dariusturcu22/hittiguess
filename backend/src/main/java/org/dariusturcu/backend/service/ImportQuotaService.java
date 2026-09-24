package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.PlaylistImportException;
import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.repository.ImportQuotaUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

// Bounds the paid metadata work a user can start through imports: each import holds at most
// MAX_SONGS_PER_IMPORT songs, and each user gets DAILY_NEW_SONG_LIMIT new songs resolved per
// UTC day across every import path. Songs already in the catalog cost nothing and don't count.
@Service
public class ImportQuotaService {

    public static final int MAX_SONGS_PER_IMPORT = 200;
    public static final int DAILY_NEW_SONG_LIMIT = 500;
    private static final int NO_ROWS_UPDATED = 0;

    private final ImportQuotaUsageRepository importQuotaUsageRepository;
    private final Clock clock;

    public ImportQuotaService(ImportQuotaUsageRepository importQuotaUsageRepository) {
        this(importQuotaUsageRepository, Clock.systemUTC());
    }

    ImportQuotaService(ImportQuotaUsageRepository importQuotaUsageRepository, Clock clock) {
        this.importQuotaUsageRepository = importQuotaUsageRepository;
        this.clock = clock;
    }

    public void requireWithinImportSize(int songCount) {
        if (songCount > MAX_SONGS_PER_IMPORT) {
            throw new PlaylistImportException("An import can hold at most " + MAX_SONGS_PER_IMPORT
                    + " songs, and this one has " + songCount + ". Split it into smaller imports.");
        }
    }

    @Transactional
    public void reserveNewSongResolutions(Long userId, int newSongCount) {
        if (newSongCount <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        boolean isReserved = newSongCount <= DAILY_NEW_SONG_LIMIT
                && importQuotaUsageRepository.addWithinDailyLimit(userId, today, newSongCount, DAILY_NEW_SONG_LIMIT)
                > NO_ROWS_UPDATED;
        if (!isReserved) {
            int usedToday = importQuotaUsageRepository.findResolutionCount(userId, today).orElse(0);
            int remainingToday = Math.max(0, DAILY_NEW_SONG_LIMIT - usedToday);
            throw new RateLimitExceededException("This import has " + newSongCount
                    + " songs that aren't in the catalog yet, and only " + remainingToday
                    + " more can be looked up today. Import fewer new songs or try again tomorrow.");
        }
    }
}
