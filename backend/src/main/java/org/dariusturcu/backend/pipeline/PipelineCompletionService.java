package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.service.GameSongList;
import org.dariusturcu.backend.service.SongCatalogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

@Service
public class PipelineCompletionService {
    private static final String NORMALIZED_KEY_LOCK_PREFIX = "song-normalized-key:";
    private static final int PRIMARY_ARTIST_INDEX = 0;

    private final PipelineJobService engine;
    private final PipelineJobRepository repository;
    private final StagePayloads payloads;
    private final SongCatalogService catalog;
    private final SongRepository songRepository;
    private final JdbcTemplate core;

    public PipelineCompletionService(PipelineJobService engine, PipelineJobRepository repository,
            StagePayloads payloads, SongCatalogService catalog, SongRepository songRepository,
            @Qualifier("coreJdbcTemplate") JdbcTemplate core) {
        this.engine = engine;
        this.repository = repository;
        this.payloads = payloads;
        this.catalog = catalog;
        this.songRepository = songRepository;
        this.core = core;
    }

    @Transactional
    public void complete(PipelineJob job, String owner, JsonNode result) {
        boolean youtubeFinal = job.stage() == PipelineStage.YOUTUBE_METADATA && payloads.youtubeFinished(result);
        if (youtubeFinal && payloads.rejected(result)) {
            engine.failPermanent(job.id(), owner, "Submission rejected by the precheck");
            return;
        }
        engine.complete(job.id(), owner, result.toString(), youtubeFinal);
        PipelineRun run = repository.lockRun(job.runId()).orElseThrow();
        if (run.status() != PipelineRunStatus.ACTIVE || run.readyToSaveAt() == null) {
            return;
        }
        SongMetadataResponse finalMetadata = payloads.finalMetadata(engine.jobsOfRun(run.id()));
        Integer playingSongYear = releaseYearOfSongInActiveGame(run.youtubeId());
        int savedYear = playingSongYear != null ? playingSongYear
                : catalog.persist(run.youtubeId(), withSameSongMatch(run.youtubeId(), finalMetadata), null).getReleaseYear();
        core.update("""
                UPDATE pending_imports SET status = 'DONE', patient_year = ?, processed_at = now() AT TIME ZONE 'UTC',
                    failure_reason = NULL
                WHERE youtube_id = ? AND origin = ? AND status IN ('PENDING', 'PROCESSING')
                """, savedYear, run.youtubeId(), run.origin().name());
        repository.finishRun(run.id(), PipelineRunStatus.DONE, Instant.now());
    }

    // A game reads a song's year, title, and artists live while it runs, so a finished run never
    // rewrites a song an active game holds. The run still finishes and the song keeps its stored
    // values; a recheck started after the game ends picks it up. The same song lookup covers
    // alternate video IDs.
    private Integer releaseYearOfSongInActiveGame(String youtubeId) {
        return core.queryForList("""
                SELECT song.release_year FROM songs song
                WHERE (song.youtube_id = ?
                       OR song.id IN (SELECT alternate.song_id FROM alternate_youtube_ids alternate WHERE alternate.youtube_id = ?))
                  AND (EXISTS (SELECT FROM rounds WHERE rounds.song_id = song.id)
                       OR EXISTS (SELECT FROM player_cards WHERE player_cards.song_id = song.id)
                       OR EXISTS (SELECT FROM game_session_song_queue queue WHERE queue.song_id = song.id))
                ORDER BY song.id
                """, Integer.class, youtubeId, youtubeId).stream().findFirst().orElse(null);
    }

    // Two videos of one song can finish the pipeline in parallel or miss the AI's embedding
    // match, so the final artist and title are looked up among every stored song in any status
    // by the indexed normalized key. The key's advisory lock is held until commit, so two
    // completions of one key save one song.
    private SongMetadataResponse withSameSongMatch(String youtubeId, SongMetadataResponse metadata) {
        if (metadata.canonicalSongId() != null || metadata.title() == null || metadata.title().isBlank()
                || metadata.mainArtists() == null || metadata.mainArtists().isEmpty()) {
            return metadata;
        }
        String normalizedKey = GameSongList.normalizedKey(metadata.mainArtists().get(PRIMARY_ARTIST_INDEX), metadata.title());
        songRepository.acquireTransactionLock(NORMALIZED_KEY_LOCK_PREFIX + normalizedKey);
        return songRepository.findOthersWithNormalizedKey(normalizedKey, youtubeId).stream().findFirst()
                .map(sameSong -> new SongMetadataResponse(
                        metadata.title(), metadata.mainArtists(), metadata.featuredArtists(), metadata.releaseYear(),
                        metadata.color(), metadata.confidence(), metadata.source(), metadata.reasoning(),
                        metadata.verificationStatus(), metadata.sitelinksCount(), metadata.durationSeconds(),
                        sameSong.getId()))
                .orElse(metadata);
    }
}
