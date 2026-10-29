package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.admin.PipelineControlResultDTO;
import org.dariusturcu.backend.model.admin.PipelineRunDTO;
import org.dariusturcu.backend.model.admin.SourceFindingDTO;
import org.dariusturcu.backend.model.admin.RecheckSongsRequest;
import org.dariusturcu.backend.model.admin.UpdateCatalogSongRequest;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AdminSongPipelineService {
    public record Findings(List<SourceFindingDTO> sources, String reviewReason) { }
    static final int MINIMUM_YEAR = 1000;
    private static final String SONG_LOCK_PREFIX = "song-youtube-id:";
    private final JdbcTemplate core;
    private final PipelineJobRepository jobs;
    private final PipelineJobService engine;
    private final AdminPipelineService pipeline;
    private final ObjectMapper mapper;

    public AdminSongPipelineService(@Qualifier("coreJdbcTemplate") JdbcTemplate core, PipelineJobRepository jobs,
            PipelineJobService engine, AdminPipelineService pipeline, ObjectMapper mapper) {
        this.core = core; this.jobs = jobs; this.engine = engine; this.pipeline = pipeline; this.mapper = mapper;
    }

    public Findings findings(long songId) {
        String youtubeId = song(songId).get("youtube_id").toString();
        List<Long> runIds = core.queryForList("SELECT id FROM pipeline_runs WHERE youtube_id = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                Long.class, youtubeId);
        if (runIds.isEmpty()) { return new Findings(null, null); }
        List<PipelineJob> results = jobs.jobsOfRun(runIds.getFirst());
        List<SourceFindingDTO> sources = results.stream().map(job -> new SourceFindingDTO(job.stage(), job.status(),
                job.resultJson() == null ? null : mapper.readerForMapOf(Object.class).readValue(job.resultJson()), PipelineJobFailures.visibleReason(job))).toList();
        String reason = results.stream().filter(job -> job.stage() == PipelineStage.LLM_RECONCILIATION
                        || job.stage() == PipelineStage.YOUTUBE_METADATA).map(PipelineJob::resultJson)
                .filter(java.util.Objects::nonNull).map(mapper::readTree).map(this::reason)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (reason == null && results.stream().anyMatch(job -> job.stage() == PipelineStage.LLM_RECONCILIATION
                && job.status() == PipelineJobStatus.SKIPPED)) {
            reason = "MusicBrainz, Discogs, and Wikidata returned the same earliest year.";
        }
        return new Findings(sources, reason);
    }

    private String reason(JsonNode result) {
        JsonNode content = result.path("content");
        if (!content.isObject()) { content = result.path("result").path("content"); }
        return content.path("reasoning").isTextual() ? content.path("reasoning").asText() : null;
    }

    @Transactional
    public PipelineRunDTO rerun(long songId) {
        return pipeline.runNow(song(songId).get("youtube_id").toString(), PendingImportOrigin.ADMIN_RECHECK);
    }

    @Transactional
    public PipelineControlResultDTO update(long songId, UpdateCatalogSongRequest request) {
        if (request == null || (request.releaseYear() == null && request.verificationStatus() == null)) {
            throw new IllegalArgumentException("A release year or verification status is required");
        }
        if (request.releaseYear() != null && (request.releaseYear() < MINIMUM_YEAR || request.releaseYear() > Year.now().getValue())) {
            throw new IllegalArgumentException("Release year must be between " + MINIMUM_YEAR + " and the current year");
        }
        Map<String, Object> existing = song(songId);
        lockSong(existing.get("youtube_id").toString());
        existing = core.queryForMap("SELECT * FROM songs WHERE id = ? FOR UPDATE", songId);
        int year = request.releaseYear() == null ? ((Number) existing.get("release_year")).intValue() : request.releaseYear();
        String status = request.verificationStatus() == null ? existing.get("verification_status").toString() : request.verificationStatus().name();
        if (VerificationStatus.VERIFIED.name().equals(status) && year < MINIMUM_YEAR) {
            throw new IllegalArgumentException("A verified song needs a release year");
        }
        core.update("UPDATE songs SET release_year = ?, verification_status = ?, proposed_release_year = NULL, proposed_at = NULL WHERE id = ?", year, status, songId);
        return new PipelineControlResultDTO(1);
    }

    @Transactional
    public PipelineControlResultDTO delete(long songId) {
        String youtubeId = song(songId).get("youtube_id").toString();
        List<String> videoIds = new ArrayList<>(core.queryForList("SELECT youtube_id FROM alternate_youtube_ids WHERE song_id = ?", String.class, songId));
        videoIds.add(youtubeId);
        videoIds.stream().sorted().forEach(jobs::lockVideo);
        videoIds.stream().flatMap(video -> core.queryForList(
                        "SELECT id FROM pipeline_runs WHERE youtube_id = ? AND status IN ('ACTIVE', 'FAILED')", Long.class, video).stream())
                .sorted().forEach(engine::discardRun);
        lockSong(youtubeId);
        core.queryForObject("SELECT id FROM songs WHERE id = ? FOR UPDATE", Long.class, songId);
        boolean playing = isSongInActiveGame(songId);
        if (playing) { throw new ConflictException("The song is used by an active game"); }
        try {
            core.update("DELETE FROM song_playlists WHERE song_id = ?", songId);
            core.update("DELETE FROM alternate_youtube_ids WHERE song_id = ?", songId);
            core.update("DELETE FROM song_reports WHERE song_id = ?", songId);
            core.update("DELETE FROM song_confirmations WHERE song_id = ?", songId);
            core.update("DELETE FROM song_artists WHERE song_id = ?", songId);
            core.update("DELETE FROM songs WHERE id = ?", songId);
        } catch (DataIntegrityViolationException referenced) {
            throw new ConflictException("The song is still referenced by another operation");
        }
        return new PipelineControlResultDTO(1);
    }

    private Map<String, Object> song(long songId) {
        return core.queryForList("SELECT id, youtube_id, release_year, verification_status FROM songs WHERE id = ?", songId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, songId));
    }

    @Transactional
    public PipelineControlResultDTO dismissProposal(long songId) {
        song(songId);
        core.update("UPDATE songs SET proposed_release_year = NULL, proposed_at = NULL WHERE id = ?", songId);
        return new PipelineControlResultDTO(1);
    }

    // Each run is queued in its own transaction, so a recheck of the whole catalog never
    // holds one long transaction and every queued run is visible to the stage workers at once.
    public PipelineControlResultDTO recheck(RecheckSongsRequest request) {
        boolean recheckAll = request != null && Boolean.TRUE.equals(request.all());
        if (request == null || (!recheckAll && (request.songIds() == null || request.songIds().isEmpty()))) {
            throw new IllegalArgumentException("Either all must be true or songIds must not be empty");
        }
        String songFilter = recheckAll ? ""
                : "AND song.id IN (" + String.join(",", Collections.nCopies(request.songIds().size(), "?")) + ")";
        Object[] parameters = recheckAll ? new Object[0] : request.songIds().toArray();
        List<String> youtubeIds = core.queryForList("""
                SELECT song.youtube_id FROM songs song
                WHERE song.youtube_id IS NOT NULL AND song.youtube_id <> ''
                  AND NOT EXISTS (SELECT FROM rounds WHERE rounds.song_id = song.id)
                  AND NOT EXISTS (SELECT FROM player_cards WHERE player_cards.song_id = song.id)
                  AND NOT EXISTS (SELECT FROM game_session_song_queue queue WHERE queue.song_id = song.id)
                  %s
                ORDER BY song.id
                """.formatted(songFilter), String.class, parameters);
        youtubeIds.forEach(youtubeId ->
                engine.startRun(youtubeId, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_RECHECK, null, null));
        return new PipelineControlResultDTO(youtubeIds.size());
    }

    public boolean isSongInActiveGame(long songId) {
        return Boolean.TRUE.equals(core.queryForObject("""
                SELECT EXISTS (SELECT FROM rounds WHERE song_id = ?)
                    OR EXISTS (SELECT FROM player_cards WHERE song_id = ?)
                    OR EXISTS (SELECT FROM game_session_song_queue WHERE song_id = ?)
                """, Boolean.class, songId, songId, songId));
    }

    private void lockSong(String youtubeId) {
        core.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) AS acquired", Integer.class, SONG_LOCK_PREFIX + youtubeId);
    }
}
