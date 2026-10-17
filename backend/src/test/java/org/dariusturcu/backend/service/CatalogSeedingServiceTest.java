package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.dariusturcu.backend.pipeline.PipelineJobService;
import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.ImportBatchKind;
import org.dariusturcu.backend.pipeline.PipelineRun;
import org.dariusturcu.backend.pipeline.PipelineRunStatus;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSeedingServiceTest {

    private static final long BATCH_ID = 100;
    private static final long SUBMITTED_BATCH_ID = 41;
    private static final long OTHER_BATCH_ID = 42;
    private static final int PROVISIONAL_YEAR = 2001;
    private static final int YOUTUBE_ID_ARGUMENT = 0;
    private static final String EXTRA_VIDEO_ID = "extraId0001";
    private static final String EXPANDED_VIDEO_ID = "expandedId1";
    private static final String PLAIN_VIDEO_ID = "videoId0001";
    private static final String WATCH_URL_VIDEO_ID = "dQw4w9WgXcQ";

    @Mock
    private PendingImportRepository pendingImportRepository;
    @Mock
    private YoutubeIdLookupService youtubeIdLookupService;
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
                playlistExpansionService, songRepository, pipeline);
        org.mockito.Mockito.lenient().when(pipeline.createBatch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(BATCH_ID);
        org.mockito.Mockito.lenient().when(pipeline.startRun(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(BATCH_ID), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(invocation -> new PipelineRun(BATCH_ID, invocation.getArgument(YOUTUBE_ID_ARGUMENT), BATCH_ID,
                        PipelineLane.BACKFILL, PipelineRunStatus.ACTIVE, PendingImportOrigin.ADMIN_SEED,
                        null, java.time.Instant.now(), null, null));
    }

    @Test
    void enqueueWithARequestExpandsThePlaylistLinkAndMergesItWithTheSubmittedIds() {
        when(playlistExpansionService.expandAndMerge(eq("playlist-link"), eq(List.of(EXTRA_VIDEO_ID))))
                .thenReturn(List.of(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(anyCollection()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID)));

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

        EnqueueResultDTO result = catalogSeedingService.enqueue(
                new AdminCatalogSeedingRequest(null, List.of("https://www.youtube.com/watch?v=" + WATCH_URL_VIDEO_ID)));

        assertThat(result.enqueuedYoutubeIds()).containsExactly(WATCH_URL_VIDEO_ID);
    }

    @Test
    void enqueueWithoutAPlaylistLinkCreatesAnIdBatch() {
        when(playlistExpansionService.expandAndMerge(eq(null), eq(List.of(PLAIN_VIDEO_ID))))
                .thenReturn(List.of(PLAIN_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(anyCollection()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(PLAIN_VIDEO_ID)));

        EnqueueResultDTO result = catalogSeedingService.enqueue(
                new AdminCatalogSeedingRequest(null, List.of(PLAIN_VIDEO_ID)));

        assertThat(result.enqueuedYoutubeIds()).containsExactly(PLAIN_VIDEO_ID);
    }

    @Test
    void enqueueCreatesABatchAndReportsRunsAlreadyQueuedElsewhere() {
        when(youtubeIdLookupService.partitionKnownAndUnknown(anyCollection()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(EXTRA_VIDEO_ID, PLAIN_VIDEO_ID)));
        when(pipeline.createBatch(ImportBatchKind.ADMIN_IDS, "Admin catalog seed", null, null)).thenReturn(SUBMITTED_BATCH_ID);
        when(pipeline.startRun(EXTRA_VIDEO_ID, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, SUBMITTED_BATCH_ID, null))
                .thenReturn(new PipelineRun(SUBMITTED_BATCH_ID, EXTRA_VIDEO_ID, SUBMITTED_BATCH_ID, PipelineLane.BACKFILL,
                        PipelineRunStatus.ACTIVE, PendingImportOrigin.ADMIN_SEED, null, java.time.Instant.now(), null, null));
        when(pipeline.startRun(PLAIN_VIDEO_ID, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, SUBMITTED_BATCH_ID, null))
                .thenReturn(new PipelineRun(OTHER_BATCH_ID, PLAIN_VIDEO_ID, OTHER_BATCH_ID, PipelineLane.BACKFILL,
                        PipelineRunStatus.ACTIVE, PendingImportOrigin.ADMIN_SEED, null, java.time.Instant.now(), null, null));

        var result = catalogSeedingService.enqueue(Set.of(EXTRA_VIDEO_ID, PLAIN_VIDEO_ID));

        assertThat(result.enqueuedYoutubeIds()).containsExactly(EXTRA_VIDEO_ID);
        assertThat(result.skippedAlreadyQueuedYoutubeIds()).containsExactly(PLAIN_VIDEO_ID);
        org.mockito.Mockito.verifyNoInteractions(pendingImportRepository);
    }

    @Test
    void rechecksCarryTheirProvisionalYearInTheUserLaneAndKeepHistory() {
        catalogSeedingService.enqueuePatientRecheck(PLAIN_VIDEO_ID, PendingImportOrigin.FAST_TIER_RECHECK, PROVISIONAL_YEAR);
        org.mockito.Mockito.verify(pipeline).startRun(PLAIN_VIDEO_ID, PipelineLane.USER_IMPORT,
                PendingImportOrigin.FAST_TIER_RECHECK, null, PROVISIONAL_YEAR);
        org.mockito.Mockito.verify(pendingImportRepository).save(org.mockito.ArgumentMatchers.any(PendingImport.class));
    }

}
