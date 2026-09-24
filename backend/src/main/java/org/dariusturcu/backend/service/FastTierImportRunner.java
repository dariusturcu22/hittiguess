package org.dariusturcu.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.ai.AiFastDateResponse;
import org.dariusturcu.backend.model.ai.AiIdentifiedSong;
import org.dariusturcu.backend.model.ai.AiIdentifyResponse;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

/**
 * Resolves a user import's new songs on the fast tier, all of them in parallel. Every
 * song's identify call is dispatched at once onto a bounded pool, and each song moves
 * to its year lookup the moment its own identify returns, so no song ever waits on
 * another song's progress, only on a free worker and the AI service's per-source
 * pacing. A resolved song is handed to the listener straight away and queued for the
 * patient recheck with its provisional year. A verified duplicate skips the year
 * lookup, and a song neither fast lane can date falls back to the full pipeline so it
 * still gets a chance. See DECISIONS.md's fast-tier import entry.
 */
@Slf4j
@Service
public class FastTierImportRunner {

    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final int FULL_PIPELINE_FALLBACK_CONCURRENCY = 1;

    /** Receives each song's progress, called from the worker threads as it happens. */
    public interface Listener {
        void identifying(String youtubeId);

        void dating(String youtubeId);

        void resolved(String youtubeId, Song song);

        void unresolved(String youtubeId);
    }

    private final SongMetadataService songMetadataService;
    private final SongResolutionService songResolutionService;
    private final CatalogSeedingService catalogSeedingService;
    private final MetadataPriorityCoordinator metadataPriorityCoordinator;
    private final TaskExecutor identifyExecutor;
    private final TaskExecutor datingExecutor;
    // The full pipeline's endpoint allows 30 calls a minute across the whole backend and
    // each call takes tens of seconds, so fallbacks run one at a time.
    private final Semaphore fullPipelineFallbackPermit = new Semaphore(FULL_PIPELINE_FALLBACK_CONCURRENCY, true);

    public FastTierImportRunner(
            SongMetadataService songMetadataService,
            SongResolutionService songResolutionService,
            CatalogSeedingService catalogSeedingService,
            MetadataPriorityCoordinator metadataPriorityCoordinator,
            @Qualifier("fastTierIdentifyExecutor") TaskExecutor identifyExecutor,
            @Qualifier("fastTierDatingExecutor") TaskExecutor datingExecutor) {
        this.songMetadataService = songMetadataService;
        this.songResolutionService = songResolutionService;
        this.catalogSeedingService = catalogSeedingService;
        this.metadataPriorityCoordinator = metadataPriorityCoordinator;
        this.identifyExecutor = identifyExecutor;
        this.datingExecutor = datingExecutor;
    }

    // Blocks until every song has settled. The caller's security context travels with
    // each task, since listeners may act as the importing user.
    public void resolveAll(List<String> youtubeIds, User addedBy, Listener listener) {
        if (youtubeIds.isEmpty()) {
            return;
        }
        SecurityContext callerContext = SecurityContextHolder.getContext();
        CountDownLatch settledSongs = new CountDownLatch(youtubeIds.size());
        metadataPriorityCoordinator.beginOnTheSpotWork();
        try {
            for (String youtubeId : youtubeIds) {
                identifyExecutor.execute(new DelegatingSecurityContextRunnable(
                        () -> identifyThenDate(youtubeId, addedBy, listener, settledSongs, callerContext), callerContext));
            }
            settledSongs.await();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            log.warn("Fast-tier import interrupted with {} songs unsettled", settledSongs.getCount());
        } finally {
            metadataPriorityCoordinator.endOnTheSpotWork();
        }
    }

    private void identifyThenDate(String youtubeId, User addedBy, Listener listener, CountDownLatch settledSongs,
                                  SecurityContext callerContext) {
        boolean handedToDating = false;
        try {
            listener.identifying(youtubeId);
            Optional<AiIdentifyResponse> identifyResponse = songMetadataService.identifyByYoutubeId(youtubeId);
            if (identifyResponse.isEmpty() || !SUCCESS_STATUS.equals(identifyResponse.get().status())) {
                listener.unresolved(youtubeId);
                return;
            }
            AiIdentifyResponse identified = identifyResponse.get();
            if (identified.duplicate() != null) {
                listener.resolved(youtubeId, songResolutionService.persistDuplicateAnswer(youtubeId, identified.duplicate(), addedBy));
                return;
            }
            if (identified.identified() == null) {
                listener.unresolved(youtubeId);
                return;
            }
            listener.dating(youtubeId);
            datingExecutor.execute(new DelegatingSecurityContextRunnable(
                    () -> date(youtubeId, identified.identified(), addedBy, listener, settledSongs), callerContext));
            handedToDating = true;
        } catch (RuntimeException failure) {
            log.warn("Fast-tier identify failed for {}", youtubeId, failure);
            listener.unresolved(youtubeId);
        } finally {
            if (!handedToDating) {
                settledSongs.countDown();
            }
        }
    }

    private Optional<Song> resolveThroughFullPipeline(String youtubeId, User addedBy) {
        fullPipelineFallbackPermit.acquireUninterruptibly();
        try {
            return songResolutionService.resolveAndPersist(youtubeId, addedBy);
        } finally {
            fullPipelineFallbackPermit.release();
        }
    }

    private void date(String youtubeId, AiIdentifiedSong identified, User addedBy, Listener listener,
                      CountDownLatch settledSongs) {
        try {
            Optional<AiFastDateResponse> fastDate = songMetadataService.dateFast(identified.title(), identified.mainArtists());
            if (fastDate.isPresent() && fastDate.get().releaseYear() != null) {
                Song song = songResolutionService.persistFastTierAnswer(youtubeId, identified, fastDate.get(), addedBy);
                catalogSeedingService.enqueuePatientRecheck(youtubeId, PendingImportOrigin.FAST_TIER_RECHECK, song.getReleaseYear());
                listener.resolved(youtubeId, song);
                return;
            }
            Optional<Song> patientSong = resolveThroughFullPipeline(youtubeId, addedBy);
            if (patientSong.isPresent()) {
                listener.resolved(youtubeId, patientSong.get());
            } else {
                listener.unresolved(youtubeId);
            }
        } catch (RuntimeException failure) {
            log.warn("Fast-tier year lookup failed for {}", youtubeId, failure);
            listener.unresolved(youtubeId);
        } finally {
            settledSongs.countDown();
        }
    }
}
