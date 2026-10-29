package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.song.AdminCatalogPageDTO;
import org.dariusturcu.backend.model.song.AdminSongDetailDTO;
import org.dariusturcu.backend.model.song.CatalogAddedByKind;
import org.dariusturcu.backend.model.song.CatalogPipeline;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.SongReportStatus;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.repository.AdminCatalogRow;
import org.dariusturcu.backend.repository.AdminSongCatalogRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongReportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSongCatalogServiceTest {

    private static final long VERIFIED_COUNT = 30;
    private static final long NEEDS_REVIEW_COUNT = 12;
    private static final long IN_QUEUE_COUNT = 5;
    private static final long PROCESSING_COUNT = 2;
    private static final long ERROR_COUNT = 1;
    private static final int SECOND_PAGE = 1;
    private static final long SECOND_PAGE_OFFSET = 25;
    private static final int SMALL_PAGE_SIZE = 10;
    private static final int NEEDS_REVIEW_PAGES_AT_SMALL_SIZE = 2;

    private static final long SONG_ID = 7;
    private static final String YOUTUBE_ID = "zimi-yt-id";
    private static final int UNRESOLVED_RELEASE_YEAR = 0;
    private static final long OPEN_REPORTS = 3;
    private static final Instant ENQUEUED_AT = Instant.parse("2026-10-15T09:14:00Z");

    private final AdminSongCatalogRepository catalogRepository = mock(AdminSongCatalogRepository.class);
    private final SongRepository songRepository = mock(SongRepository.class);
    private final PendingImportRepository pendingImportRepository = mock(PendingImportRepository.class);
    private final SongReportRepository songReportRepository = mock(SongReportRepository.class);
    private final org.dariusturcu.backend.pipeline.AdminSongPipelineService pipeline = mock(org.dariusturcu.backend.pipeline.AdminSongPipelineService.class);
    private final AdminSongCatalogService service = new AdminSongCatalogService(catalogRepository,
            songRepository, pendingImportRepository, songReportRepository, pipeline);

    private static Map<CatalogSongStatus, Long> sampleCounts() {
        Map<CatalogSongStatus, Long> counts = new EnumMap<>(CatalogSongStatus.class);
        counts.put(CatalogSongStatus.VERIFIED, VERIFIED_COUNT);
        counts.put(CatalogSongStatus.NEEDS_REVIEW, NEEDS_REVIEW_COUNT);
        counts.put(CatalogSongStatus.IN_QUEUE, IN_QUEUE_COUNT);
        counts.put(CatalogSongStatus.PROCESSING, PROCESSING_COUNT);
        counts.put(CatalogSongStatus.ERROR, ERROR_COUNT);
        return counts;
    }

    @Test
    void searchTextIsTrimmedAndBlankMeansNoSearch() {
        assertThat(AdminSongCatalogService.normalizeSearchText("  zim  ")).isEqualTo("zim");
        assertThat(AdminSongCatalogService.normalizeSearchText(null)).isNull();
        assertThat(AdminSongCatalogService.normalizeSearchText("   ")).isNull();
    }

    @Test
    void searchTextShorterThanTheMinimumAfterTrimmingIsRejected() {
        assertThatThrownBy(() -> AdminSongCatalogService.normalizeSearchText(" zi "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(AdminSongCatalogService.MINIMUM_SEARCH_LENGTH));
    }

    @Test
    void thePageSizeDefaultsToTheMaximumAndRejectsAnythingOutsideOneToTheMaximum() {
        assertThat(AdminSongCatalogService.resolvePageSize(null)).isEqualTo(AdminSongCatalogService.DEFAULT_PAGE_SIZE);
        assertThat(AdminSongCatalogService.resolvePageSize(AdminSongCatalogService.MAXIMUM_PAGE_SIZE))
                .isEqualTo(AdminSongCatalogService.MAXIMUM_PAGE_SIZE);
        assertThatThrownBy(() -> AdminSongCatalogService.resolvePageSize(AdminSongCatalogService.MAXIMUM_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdminSongCatalogService.resolvePageSize(AdminSongCatalogService.MINIMUM_PAGE_SIZE - 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativePageIsRejected() {
        assertThat(AdminSongCatalogService.resolvePage(null)).isEqualTo(AdminSongCatalogService.FIRST_PAGE);
        assertThatThrownBy(() -> AdminSongCatalogService.resolvePage(AdminSongCatalogService.FIRST_PAGE - 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInvalidRequestNeverReachesTheDatabase() {
        assertThatThrownBy(() -> service.search("ab", null, null, null)).isInstanceOf(IllegalArgumentException.class);

        verify(catalogRepository, never()).countByStatus(any());
        verify(catalogRepository, never()).findPage(any(), any(), anyInt(), anyLong());
    }

    @Test
    void totalsFollowTheStatusFilterWhileCountsCoverEveryStatus() {
        when(catalogRepository.countByStatus("zim")).thenReturn(sampleCounts());
        when(catalogRepository.findPage(eq("zim"), eq(CatalogSongStatus.NEEDS_REVIEW), eq(SMALL_PAGE_SIZE), anyLong()))
                .thenReturn(List.of());

        AdminCatalogPageDTO filteredPage = service.search(" zim ", CatalogSongStatus.NEEDS_REVIEW, null, SMALL_PAGE_SIZE);

        long allCount = VERIFIED_COUNT + NEEDS_REVIEW_COUNT + IN_QUEUE_COUNT + PROCESSING_COUNT + ERROR_COUNT;
        assertThat(filteredPage.totalElements()).isEqualTo(NEEDS_REVIEW_COUNT);
        assertThat(filteredPage.totalPages()).isEqualTo(NEEDS_REVIEW_PAGES_AT_SMALL_SIZE);
        assertThat(filteredPage.counts().all()).isEqualTo(allCount);
        assertThat(filteredPage.counts().verified()).isEqualTo(VERIFIED_COUNT);
        assertThat(filteredPage.counts().error()).isEqualTo(ERROR_COUNT);
    }

    @Test
    void theOffsetIsThePageTimesThePageSize() {
        when(catalogRepository.countByStatus(null)).thenReturn(sampleCounts());
        when(catalogRepository.findPage(null, null, AdminSongCatalogService.DEFAULT_PAGE_SIZE, SECOND_PAGE_OFFSET))
                .thenReturn(List.of());

        AdminCatalogPageDTO secondPage = service.search(null, null, SECOND_PAGE, null);

        assertThat(secondPage.page()).isEqualTo(SECOND_PAGE);
        assertThat(secondPage.size()).isEqualTo(AdminSongCatalogService.DEFAULT_PAGE_SIZE);
        verify(catalogRepository).findPage(null, null, AdminSongCatalogService.DEFAULT_PAGE_SIZE, SECOND_PAGE_OFFSET);
    }

    @Test
    void aCatalogRowWithoutPipelineEvidenceIsLegacy() {
        AdminCatalogRow legacyRow = new AdminCatalogRow(
                "song:" + SONG_ID,
                SONG_ID,
                YOUTUBE_ID,
                "Hey Mama",
                List.of("David Guetta"),
                2015,
                CatalogSongStatus.VERIFIED,
                false,
                false,
                null,
                false,
                false,
                ENQUEUED_AT,
                null);
        when(catalogRepository.countByStatus(null)).thenReturn(sampleCounts());
        when(catalogRepository.findPage(null, null, AdminSongCatalogService.DEFAULT_PAGE_SIZE,
                AdminSongCatalogService.FIRST_PAGE)).thenReturn(List.of(legacyRow));

        AdminCatalogPageDTO page = service.search(null, null, null, null);

        assertThat(page.items()).singleElement().satisfies(song -> {
            assertThat(song.pipeline()).isEqualTo(CatalogPipeline.LEGACY);
            assertThat(song.addedByKind()).isEqualTo(CatalogAddedByKind.LEGACY);
        });
    }

    @Test
    void theDetailDerivesStatusPipelineAndAddedByFromTheQueueHistory() {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setTitle("Zi-mi");
        song.setYoutubeId(YOUTUBE_ID);
        song.setReleaseYear(UNRESOLVED_RELEASE_YEAR);
        song.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        SongArtist artist = new SongArtist();
        artist.setName("Loredana");
        song.getArtists().add(artist);
        PendingImport recheck = new PendingImport();
        recheck.setId(SONG_ID);
        recheck.setYoutubeId(YOUTUBE_ID);
        recheck.setOrigin(PendingImportOrigin.FAST_TIER_RECHECK);
        recheck.setStatus(PendingImportStatus.PENDING);
        recheck.setEnqueuedAt(ENQUEUED_AT);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(catalogRepository.pipelineHistory(YOUTUBE_ID)).thenReturn(List.of(recheck));
        when(pendingImportRepository.findByYoutubeIdOrderByEnqueuedAtAscIdAsc(YOUTUBE_ID)).thenReturn(List.of(recheck));
        when(songReportRepository.countBySongIdAndStatus(SONG_ID, SongReportStatus.OPEN)).thenReturn(OPEN_REPORTS);

        when(pipeline.findings(SONG_ID)).thenReturn(new org.dariusturcu.backend.pipeline.AdminSongPipelineService.Findings(null, null));
        AdminSongDetailDTO detail = service.detail(SONG_ID);

        assertThat(detail.status()).isEqualTo(CatalogSongStatus.IN_QUEUE);
        assertThat(detail.pipeline()).isEqualTo(CatalogPipeline.FAST);
        assertThat(detail.addedByKind()).isEqualTo(CatalogAddedByKind.UNKNOWN);
        assertThat(detail.releaseYear()).isNull();
        assertThat(detail.artists()).containsExactly("Loredana");
        assertThat(detail.verificationStatus()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
        assertThat(detail.openReportCount()).isEqualTo(OPEN_REPORTS);
        assertThat(detail.queueHistory()).singleElement()
                .satisfies(entry -> assertThat(entry.origin()).isEqualTo(PendingImportOrigin.FAST_TIER_RECHECK));
    }

    @Test
    void retainedHistoryWithoutARunDoesNotSetTheCurrentCatalogState() {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setYoutubeId(YOUTUBE_ID);
        song.setVerificationStatus(VerificationStatus.VERIFIED);
        PendingImport legacyHistory = new PendingImport();
        legacyHistory.setId(SONG_ID);
        legacyHistory.setOrigin(PendingImportOrigin.ADMIN_SEED);
        legacyHistory.setStatus(PendingImportStatus.PENDING);
        legacyHistory.setEnqueuedAt(ENQUEUED_AT);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(catalogRepository.pipelineHistory(YOUTUBE_ID)).thenReturn(List.of());
        when(pendingImportRepository.findByYoutubeIdOrderByEnqueuedAtAscIdAsc(YOUTUBE_ID))
                .thenReturn(List.of(legacyHistory));
        when(pipeline.findings(SONG_ID)).thenReturn(new org.dariusturcu.backend.pipeline.AdminSongPipelineService.Findings(null, null));

        AdminSongDetailDTO detail = service.detail(SONG_ID);

        assertThat(detail.status()).isEqualTo(CatalogSongStatus.VERIFIED);
        assertThat(detail.pipeline()).isEqualTo(CatalogPipeline.UNKNOWN);
        assertThat(detail.addedByKind()).isEqualTo(CatalogAddedByKind.UNKNOWN);
        assertThat(detail.queueHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.origin()).isEqualTo(PendingImportOrigin.ADMIN_SEED);
            assertThat(entry.status()).isEqualTo(PendingImportStatus.PENDING);
        });
    }

    @Test
    void aSongWithNoPipelineOrQueueRowsIsLegacy() {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setYoutubeId(YOUTUBE_ID);
        song.setVerificationStatus(VerificationStatus.VERIFIED);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(catalogRepository.pipelineHistory(YOUTUBE_ID)).thenReturn(List.of());
        when(pendingImportRepository.findByYoutubeIdOrderByEnqueuedAtAscIdAsc(YOUTUBE_ID)).thenReturn(List.of());
        when(pipeline.findings(SONG_ID)).thenReturn(new org.dariusturcu.backend.pipeline.AdminSongPipelineService.Findings(null, null));

        AdminSongDetailDTO detail = service.detail(SONG_ID);

        assertThat(detail.status()).isEqualTo(CatalogSongStatus.VERIFIED);
        assertThat(detail.pipeline()).isEqualTo(CatalogPipeline.LEGACY);
        assertThat(detail.addedByKind()).isEqualTo(CatalogAddedByKind.LEGACY);
        assertThat(detail.queueHistory()).isEmpty();
    }

    @Test
    void anUnknownSongIdIsNotFound() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(SONG_ID)).isInstanceOf(ResourceNotFoundException.class);
    }
}
