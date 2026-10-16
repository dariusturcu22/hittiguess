package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.BacklogStatusDTO;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.dariusturcu.backend.pipeline.PipelineJobService;
import org.dariusturcu.backend.pipeline.PipelineSettings;
import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.ImportBatchKind;
import org.dariusturcu.backend.pipeline.PipelineRun;
import org.dariusturcu.backend.pipeline.PipelineRunStatus;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSeedingServiceTest {

    // YoutubeLinkParser only accepts exactly-11-character ids, so every submitted id
    // below is shaped like a real one rather than a short descriptive placeholder.
    private static final long DAILY_DRAIN_QUOTA = 100;
    private static final String EXTRA_VIDEO_ID = "extraId0001";
    private static final String EXPANDED_VIDEO_ID = "expandedId1";
    private static final String PLAIN_VIDEO_ID = "videoId0001";
    private static final String WATCH_URL_VIDEO_ID = "dQw4w9WgXcQ";

    @Mock
    private PendingImportRepository pendingImportRepository;
    @Mock
    private YoutubeIdLookupService youtubeIdLookupService;
    @Mock
    private PendingImportProcessor pendingImportProcessor;
    @Mock
    private PlaylistExpansionService playlistExpansionService;
    @Mock
    private SongRepository songRepository;
    @Mock
    private PipelineJobService pipeline;

    private CatalogSeedingService catalogSeedingService;

    @BeforeEach
    void setUp() {
        catalogSeedingService = new CatalogSeedingService(pendingImportRepository, youtubeIdLookupService,
                pendingImportProcessor, new MetadataPriorityCoordinator(), playlistExpansionService, songRepository,
                new SyncTaskExecutor(), DAILY_DRAIN_QUOTA);
    }

    @Test
    void enqueueWithARequestExpandsThePlaylistLinkAndMergesItWithTheSubmittedIds() {
        when(playlistExpansionService.expandAndMerge(eq("playlist-link"), eq(List.of(EXTRA_VIDEO_ID))))
                .thenReturn(List.of(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(anyCollection()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID)));
        when(pendingImportRepository.findYoutubeIdsAlreadyQueued(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        EnqueueResultDTO result = catalogSeedingService.enqueue(
                new AdminCatalogSeedingRequest("playlist-link", List.of(EXTRA_VIDEO_ID)));

        assertThat(result.enqueuedYoutubeIds()).containsExactlyInAnyOrder(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID);
    }

    @Test
    void enqueueWithARequestParsesSubmittedLinksIntoVideoIdsBeforeMerging() {
        when(playlistExpansionService.expandAndMerge(eq(null), eq(List.of(WATCH_URL_VIDEO_ID))))
                .thenReturn(List.of(WATCH_URL_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(anyCollection()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(WATCH_URL_VIDEO_ID)));
        when(pendingImportRepository.findYoutubeIdsAlreadyQueued(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        EnqueueResultDTO result = catalogSeedingService.enqueue(
                new AdminCatalogSeedingRequest(null, List.of("https://www.youtube.com/watch?v=" + WATCH_URL_VIDEO_ID)));

        assertThat(result.enqueuedYoutubeIds()).containsExactly(WATCH_URL_VIDEO_ID);
    }

    @Test
    void enqueueWithARequestAndNoPlaylistLinkNeverCallsExpansion() {
        when(playlistExpansionService.expandAndMerge(eq(null), eq(List.of(PLAIN_VIDEO_ID))))
                .thenReturn(List.of(PLAIN_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(anyCollection()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(PLAIN_VIDEO_ID)));
        when(pendingImportRepository.findYoutubeIdsAlreadyQueued(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        EnqueueResultDTO result = catalogSeedingService.enqueue(
                new AdminCatalogSeedingRequest(null, List.of(PLAIN_VIDEO_ID)));

        assertThat(result.enqueuedYoutubeIds()).containsExactly(PLAIN_VIDEO_ID);
    }

    @Test
    void backlogStatusIncludesRecentQueueItemsForTheAdminView() {
        PendingImport pendingImport = new PendingImport();
        pendingImport.setYoutubeId(PLAIN_VIDEO_ID);
        pendingImport.setStatus(PendingImportStatus.PROCESSING);
        when(pendingImportRepository.countByStatus(PendingImportStatus.PENDING)).thenReturn(12L);
        when(pendingImportRepository.countByStatusAndProcessedAtAfter(eq(PendingImportStatus.DONE), org.mockito.ArgumentMatchers.any()))
                .thenReturn(4L);
        when(pendingImportRepository.findTop7ByOrderByEnqueuedAtDesc()).thenReturn(List.of(pendingImport));

        BacklogStatusDTO status = catalogSeedingService.backlogStatus();

        assertThat(status.queueItems()).singleElement().satisfies(queueItem -> {
            assertThat(queueItem.youtubeId()).isEqualTo(PLAIN_VIDEO_ID);
            assertThat(queueItem.status()).isEqualTo(PendingImportStatus.PROCESSING);
        });
    }

    private CatalogSeedingService stagedService() {
        return new CatalogSeedingService(pendingImportRepository, youtubeIdLookupService, pendingImportProcessor,
                new MetadataPriorityCoordinator(), playlistExpansionService, songRepository, new SyncTaskExecutor(),
                DAILY_DRAIN_QUOTA, new PipelineSettings(true, PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS), pipeline);
    }

    @Test
    void flagOnEnqueueCreatesABatchAndReportsRunsAlreadyQueuedElsewhere() {
        long batchId = 41;
        long otherBatchId = 42;
        when(youtubeIdLookupService.partitionKnownAndUnknown(anyCollection()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(EXTRA_VIDEO_ID, PLAIN_VIDEO_ID)));
        when(pipeline.createBatch(ImportBatchKind.ADMIN_IDS, "Admin catalog seed", null, null)).thenReturn(batchId);
        when(pipeline.startRun(EXTRA_VIDEO_ID, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, batchId, null))
                .thenReturn(new PipelineRun(batchId, EXTRA_VIDEO_ID, batchId, PipelineLane.BACKFILL,
                        PipelineRunStatus.ACTIVE, PendingImportOrigin.ADMIN_SEED, null, java.time.Instant.now(), null, null));
        when(pipeline.startRun(PLAIN_VIDEO_ID, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, batchId, null))
                .thenReturn(new PipelineRun(otherBatchId, PLAIN_VIDEO_ID, otherBatchId, PipelineLane.BACKFILL,
                        PipelineRunStatus.ACTIVE, PendingImportOrigin.ADMIN_SEED, null, java.time.Instant.now(), null, null));

        var result = stagedService().enqueue(Set.of(EXTRA_VIDEO_ID, PLAIN_VIDEO_ID));

        assertThat(result.enqueuedYoutubeIds()).containsExactly(EXTRA_VIDEO_ID);
        assertThat(result.skippedAlreadyQueuedYoutubeIds()).containsExactly(PLAIN_VIDEO_ID);
        org.mockito.Mockito.verifyNoInteractions(pendingImportRepository);
    }

    @Test
    void flagOnRechecksCarryTheirProvisionalYearInTheUserLaneAndKeepHistory() {
        int provisionalYear = 2001;
        stagedService().enqueuePatientRecheck(PLAIN_VIDEO_ID, PendingImportOrigin.FAST_TIER_RECHECK, provisionalYear);
        org.mockito.Mockito.verify(pipeline).startRun(PLAIN_VIDEO_ID, PipelineLane.USER_IMPORT,
                PendingImportOrigin.FAST_TIER_RECHECK, null, provisionalYear);
        org.mockito.Mockito.verify(pendingImportRepository).save(org.mockito.ArgumentMatchers.any(PendingImport.class));
    }

    @Test
    void flagOnBlocksBothLegacyDrainEntryPointsWithoutReadingTheOldQueue() {
        assertThat(stagedService().drainBacklog()).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stagedService().startDrainNow())
                .isInstanceOf(org.dariusturcu.backend.exception.ConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(pendingImportProcessor, pendingImportRepository);
    }
}
