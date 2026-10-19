package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.PlaylistImportException;
import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.repository.ImportQuotaUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportQuotaServiceTest {

    private static final Long USER_ID = 4L;
    private static final Instant NOW = Instant.parse("2026-09-24T23:30:00Z");
    private static final LocalDate TODAY_IN_UTC = LocalDate.of(2026, 9, 24);
    private static final int NEW_SONG_COUNT = 3;
    private static final int SONGS_LEFT_TODAY = 2;
    private static final int ALREADY_USED_TODAY = ImportQuotaService.DAILY_NEW_SONG_LIMIT - SONGS_LEFT_TODAY;
    private static final int NO_NEW_SONGS = 0;
    private static final int ROW_UPDATED = 1;
    private static final int NO_ROW_UPDATED = 0;

    @Mock
    private ImportQuotaUsageRepository importQuotaUsageRepository;

    private ImportQuotaService importQuotaService;

    @BeforeEach
    void setUp() {
        importQuotaService = new ImportQuotaService(importQuotaUsageRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void anImportAtTheCapIsAllowed() {
        assertThatCode(() -> importQuotaService.requireWithinImportSize(ImportQuotaService.MAX_SONGS_PER_IMPORT))
                .doesNotThrowAnyException();
    }

    @Test
    void anImportOverTheCapIsRefused() {
        assertThatThrownBy(() -> importQuotaService.requireWithinImportSize(ImportQuotaService.MAX_SONGS_PER_IMPORT + 1))
                .isInstanceOf(PlaylistImportException.class);
    }

    @Test
    void newSongsWithinTodaysLimitAreReservedForTheUtcDay() {
        when(importQuotaUsageRepository.addWithinDailyLimit(
                USER_ID, TODAY_IN_UTC, NEW_SONG_COUNT, ImportQuotaService.DAILY_NEW_SONG_LIMIT)).thenReturn(ROW_UPDATED);

        assertThatCode(() -> importQuotaService.reserveNewSongResolutions(USER_ID, NEW_SONG_COUNT))
                .doesNotThrowAnyException();
    }

    @Test
    void newSongsPastTodaysLimitAreRefusedWithWhatIsLeft() {
        when(importQuotaUsageRepository.addWithinDailyLimit(
                USER_ID, TODAY_IN_UTC, NEW_SONG_COUNT, ImportQuotaService.DAILY_NEW_SONG_LIMIT)).thenReturn(NO_ROW_UPDATED);
        when(importQuotaUsageRepository.findResolutionCount(USER_ID, TODAY_IN_UTC)).thenReturn(Optional.of(ALREADY_USED_TODAY));

        assertThatThrownBy(() -> importQuotaService.reserveNewSongResolutions(USER_ID, NEW_SONG_COUNT))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("only " + SONGS_LEFT_TODAY + " more");
    }

    @Test
    void moreNewSongsThanAWholeDaysLimitAreRefusedWithoutTouchingTheCount() {
        when(importQuotaUsageRepository.findResolutionCount(USER_ID, TODAY_IN_UTC)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> importQuotaService.reserveNewSongResolutions(
                USER_ID, ImportQuotaService.DAILY_NEW_SONG_LIMIT + 1))
                .isInstanceOf(RateLimitExceededException.class);
        verify(importQuotaUsageRepository, never()).addWithinDailyLimit(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    void anImportWithNoNewSongsReservesNothing() {
        importQuotaService.reserveNewSongResolutions(USER_ID, NO_NEW_SONGS);

        verify(importQuotaUsageRepository, never()).addWithinDailyLimit(anyLong(), any(), anyInt(), anyInt());
    }
}
