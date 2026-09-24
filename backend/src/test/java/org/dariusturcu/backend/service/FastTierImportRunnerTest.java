package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.ai.AiFastDateResponse;
import org.dariusturcu.backend.model.ai.AiIdentifiedSong;
import org.dariusturcu.backend.model.ai.AiIdentifyResponse;
import org.dariusturcu.backend.model.ai.AiMetadataContent;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FastTierImportRunnerTest {

    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String ERROR_STATUS = "ERROR";
    private static final long WAIT_SECONDS = 5;
    private static final int FAST_TIER_YEAR = 1998;
    private static final long FALLBACK_WORK_MILLISECONDS = 50;
    private static final String FIRST_VIDEO_ID = "firstVideo1";
    private static final String SECOND_VIDEO_ID = "secondVideo";
    private static final String THIRD_VIDEO_ID = "thirdVideo1";
    private static final List<String> THREE_VIDEO_IDS = List.of(FIRST_VIDEO_ID, SECOND_VIDEO_ID, THIRD_VIDEO_ID);

    @Mock
    private SongMetadataService songMetadataService;
    @Mock
    private SongResolutionService songResolutionService;
    @Mock
    private CatalogSeedingService catalogSeedingService;

    private final MetadataPriorityCoordinator metadataPriorityCoordinator = new MetadataPriorityCoordinator();
    private final User importingUser = new User();
    private final RecordingListener listener = new RecordingListener();
    private FastTierImportRunner runner;

    // Collects each song's final outcome, and the authentication each callback ran with.
    static class RecordingListener implements FastTierImportRunner.Listener {
        final Map<String, String> outcomes = new ConcurrentHashMap<>();
        final Map<String, Authentication> resolvedWith = new ConcurrentHashMap<>();

        @Override
        public void identifying(String youtubeId) {
        }

        @Override
        public void dating(String youtubeId) {
        }

        @Override
        public void resolved(String youtubeId, Song song) {
            outcomes.put(youtubeId, "resolved");
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                resolvedWith.put(youtubeId, authentication);
            }
        }

        @Override
        public void unresolved(String youtubeId) {
            outcomes.put(youtubeId, "unresolved");
        }
    }

    @BeforeEach
    void setUp() {
        runner = new FastTierImportRunner(songMetadataService, songResolutionService, catalogSeedingService,
                metadataPriorityCoordinator, new SimpleAsyncTaskExecutor(), new SimpleAsyncTaskExecutor());
        lenient().when(songResolutionService.persistFastTierAnswer(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> songWithYear(FAST_TIER_YEAR));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Song songWithYear(int releaseYear) {
        Song song = new Song();
        song.setReleaseYear(releaseYear);
        return song;
    }

    private static Optional<AiIdentifyResponse> identified(String youtubeId) {
        return Optional.of(new AiIdentifyResponse(SUCCESS_STATUS, "model",
                new AiIdentifiedSong("Title " + youtubeId, List.of("Artist " + youtubeId), List.of(), "111111"), null, null, null));
    }

    private static Optional<AiFastDateResponse> dated(Integer releaseYear) {
        return Optional.of(new AiFastDateResponse(releaseYear, "low", "fast-tier-musicbrainz", "musicbrainz"));
    }

    @Test
    void everySongsIdentifyIsInFlightBeforeAnyOfThemFinishes() {
        CyclicBarrier allIdentifiesStarted = new CyclicBarrier(THREE_VIDEO_IDS.size());
        when(songMetadataService.identifyByYoutubeId(anyString())).thenAnswer(invocation -> {
            allIdentifiesStarted.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return identified(invocation.getArgument(0));
        });
        when(songMetadataService.dateFast(anyString(), anyList())).thenReturn(dated(FAST_TIER_YEAR));

        runner.resolveAll(THREE_VIDEO_IDS, importingUser, listener);

        assertThat(listener.outcomes).containsOnly(
                Map.entry(FIRST_VIDEO_ID, "resolved"), Map.entry(SECOND_VIDEO_ID, "resolved"), Map.entry(THIRD_VIDEO_ID, "resolved"));
    }

    @Test
    void aSongIsDatedAsSoonAsItsOwnIdentifyReturnsWithoutWaitingForOtherSongs() {
        CountDownLatch firstSongDated = new CountDownLatch(1);
        when(songMetadataService.identifyByYoutubeId(FIRST_VIDEO_ID)).thenReturn(identified(FIRST_VIDEO_ID));
        when(songMetadataService.identifyByYoutubeId(SECOND_VIDEO_ID)).thenAnswer(invocation -> {
            boolean wasFirstDated = firstSongDated.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return wasFirstDated ? identified(SECOND_VIDEO_ID) : Optional.empty();
        });
        when(songMetadataService.dateFast(anyString(), anyList())).thenAnswer(invocation -> {
            if (invocation.getArgument(0).equals("Title " + FIRST_VIDEO_ID)) {
                firstSongDated.countDown();
            }
            return dated(FAST_TIER_YEAR);
        });

        runner.resolveAll(List.of(FIRST_VIDEO_ID, SECOND_VIDEO_ID), importingUser, listener);

        assertThat(listener.outcomes).containsEntry(SECOND_VIDEO_ID, "resolved");
    }

    @Test
    void aFailedIdentifyLeavesOnlyThatSongUnresolved() {
        when(songMetadataService.identifyByYoutubeId(FIRST_VIDEO_ID)).thenReturn(identified(FIRST_VIDEO_ID));
        when(songMetadataService.identifyByYoutubeId(SECOND_VIDEO_ID))
                .thenReturn(Optional.of(new AiIdentifyResponse(ERROR_STATUS, "model", null, null, null, null)));
        when(songMetadataService.identifyByYoutubeId(THIRD_VIDEO_ID)).thenThrow(new IllegalStateException("broken"));
        when(songMetadataService.dateFast(anyString(), anyList())).thenReturn(dated(FAST_TIER_YEAR));

        runner.resolveAll(THREE_VIDEO_IDS, importingUser, listener);

        assertThat(listener.outcomes).containsOnly(
                Map.entry(FIRST_VIDEO_ID, "resolved"), Map.entry(SECOND_VIDEO_ID, "unresolved"), Map.entry(THIRD_VIDEO_ID, "unresolved"));
    }

    @Test
    void aResolvedSongIsQueuedForThePatientRecheckWithItsFastTierYear() {
        when(songMetadataService.identifyByYoutubeId(FIRST_VIDEO_ID)).thenReturn(identified(FIRST_VIDEO_ID));
        when(songMetadataService.dateFast(anyString(), anyList())).thenReturn(dated(FAST_TIER_YEAR));

        runner.resolveAll(List.of(FIRST_VIDEO_ID), importingUser, listener);

        verify(catalogSeedingService).enqueuePatientRecheck(FIRST_VIDEO_ID, PendingImportOrigin.FAST_TIER_RECHECK, FAST_TIER_YEAR);
        assertThat(metadataPriorityCoordinator.isOnTheSpotTrafficActive()).isFalse();
    }

    @Test
    void aVerifiedDuplicateSkipsTheYearLookupAndTheRecheck() {
        AiMetadataContent duplicate = new AiMetadataContent("Title", List.of("Artist"), List.of(), 1975, "111111",
                "high", "pgvector-duplicate-match", "match", "VERIFIED", null);
        when(songMetadataService.identifyByYoutubeId(FIRST_VIDEO_ID))
                .thenReturn(Optional.of(new AiIdentifyResponse(SUCCESS_STATUS, "model", null, duplicate, null, null)));
        when(songResolutionService.persistDuplicateAnswer(eq(FIRST_VIDEO_ID), eq(duplicate), any())).thenReturn(songWithYear(1975));

        runner.resolveAll(List.of(FIRST_VIDEO_ID), importingUser, listener);

        assertThat(listener.outcomes).containsEntry(FIRST_VIDEO_ID, "resolved");
        verify(songMetadataService, never()).dateFast(anyString(), anyList());
        verify(catalogSeedingService, never()).enqueuePatientRecheck(any(), any(), any());
    }

    @Test
    void aSongNeitherLaneCanDateFallsBackToTheFullPipelineWithoutARecheck() {
        when(songMetadataService.identifyByYoutubeId(FIRST_VIDEO_ID)).thenReturn(identified(FIRST_VIDEO_ID));
        when(songMetadataService.dateFast(anyString(), anyList())).thenReturn(dated(null));
        when(songResolutionService.resolveAndPersist(FIRST_VIDEO_ID, importingUser)).thenReturn(Optional.of(songWithYear(2001)));

        runner.resolveAll(List.of(FIRST_VIDEO_ID), importingUser, listener);

        assertThat(listener.outcomes).containsEntry(FIRST_VIDEO_ID, "resolved");
        verify(catalogSeedingService, never()).enqueuePatientRecheck(any(), any(), any());
    }

    @Test
    void workerThreadsActAsTheImportingUser() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("importer", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(songMetadataService.identifyByYoutubeId(FIRST_VIDEO_ID)).thenReturn(identified(FIRST_VIDEO_ID));
        when(songMetadataService.dateFast(anyString(), anyList())).thenReturn(dated(FAST_TIER_YEAR));

        runner.resolveAll(List.of(FIRST_VIDEO_ID), importingUser, listener);

        assertThat(listener.resolvedWith).containsEntry(FIRST_VIDEO_ID, authentication);
    }

    @Test
    void songsFallingBackToTheFullPipelineRunOneAtATime() {
        AtomicInteger fallbacksInFlight = new AtomicInteger();
        AtomicInteger mostFallbacksAtOnce = new AtomicInteger();
        when(songMetadataService.identifyByYoutubeId(anyString())).thenAnswer(invocation -> identified(invocation.getArgument(0)));
        when(songMetadataService.dateFast(anyString(), anyList())).thenReturn(dated(null));
        when(songResolutionService.resolveAndPersist(anyString(), any())).thenAnswer(invocation -> {
            mostFallbacksAtOnce.accumulateAndGet(fallbacksInFlight.incrementAndGet(), Math::max);
            Thread.sleep(FALLBACK_WORK_MILLISECONDS);
            fallbacksInFlight.decrementAndGet();
            return Optional.of(songWithYear(2001));
        });

        runner.resolveAll(THREE_VIDEO_IDS, importingUser, listener);

        assertThat(listener.outcomes.values()).containsOnly("resolved");
        assertThat(mostFallbacksAtOnce.get()).isEqualTo(1);
    }
}
