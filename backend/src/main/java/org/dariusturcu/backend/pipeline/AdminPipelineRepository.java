package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.admin.ImportBatchDTO;
import org.dariusturcu.backend.model.admin.QueueJobDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AdminPipelineRepository {
    /** A stage's control row with its stage_pace row, when the stage calls a paced source. */
    public record Pace(PipelineStage stage, boolean paused, String reason, Instant cooldownUntil,
                       Integer delayMs, Integer sourceRequestMs) { }
    public record StageDepth(PipelineStage stage, long remaining) { }

    private static final String JOB_ROWS = """
            SELECT job.*, run.youtube_id,
                COALESCE(song.title, identification.result ->> 'title', run.youtube_id) AS title,
                ARRAY(SELECT jsonb_array_elements_text(COALESCE(
                    (SELECT jsonb_agg(artist.name ORDER BY artist.display_order) FROM song_artists artist WHERE artist.song_id = song.id),
                    identification.result -> 'artists', '[]'::jsonb))) AS artist_names
            FROM pipeline_jobs job
            JOIN pipeline_runs run ON run.id = job.run_id
            LEFT JOIN LATERAL (SELECT id, title FROM songs WHERE youtube_id = run.youtube_id ORDER BY id LIMIT 1) song ON true
            LEFT JOIN pipeline_jobs identification ON identification.run_id = run.id AND identification.stage = 'YOUTUBE_METADATA'
            """;
    private static final String RUN_STATE = """
            CASE WHEN run.status <> 'ACTIVE' THEN run.status
                WHEN EXISTS (SELECT FROM pipeline_jobs running WHERE running.run_id = run.id AND running.status = 'IN_PROGRESS') THEN 'IN_PROGRESS'
                WHEN EXISTS (SELECT FROM pipeline_jobs retry WHERE retry.run_id = run.id AND retry.status = 'WAITING' AND retry.attempts > 0) THEN 'RETRYING'
                ELSE 'WAITING' END
            """;
    private static final int CURRENT_TRY = 1;
    private final JdbcTemplate core;

    public AdminPipelineRepository(@Qualifier("coreJdbcTemplate") JdbcTemplate core) { this.core = core; }

    public List<Pace> pace() {
        return core.query("""
                SELECT control.stage, control.paused, control.paused_reason, pace.cooldown_until,
                    pace.current_delay_ms, pace.avg_response_ms
                FROM stage_controls control LEFT JOIN stage_pace pace ON pace.stage = control.stage
                ORDER BY control.stage
                """, (row, number) -> new Pace(PipelineStage.valueOf(row.getString("stage")), row.getBoolean("paused"),
                row.getString("paused_reason"), instant(row, "cooldown_until"), row.getObject("current_delay_ms", Integer.class),
                row.getObject("avg_response_ms", Integer.class)));
    }

    public long jobCount(PipelineStage stage, String status) {
        if (stage == PipelineStage.EMBEDDING) { return embeddingCount(status); }
        List<Object> parameters = new ArrayList<>(List.of(stage.name()));
        String filter = statusFilter(status, parameters);
        return core.queryForObject("SELECT count(*) FROM pipeline_jobs job JOIN pipeline_runs run ON run.id = job.run_id"
                + " WHERE job.stage = ?" + filter, Long.class, parameters.toArray());
    }

    public List<QueueJobDTO> jobs(PipelineStage stage, String status, int size, long offset) {
        if (stage == PipelineStage.EMBEDDING) { return embeddingJobs(status, size, offset); }
        List<Object> parameters = new ArrayList<>(List.of(stage.name()));
        String filter = statusFilter(status, parameters);
        parameters.add(size);
        parameters.add(offset);
        return core.query(JOB_ROWS + " WHERE job.stage = ?" + filter
                + " ORDER BY CASE WHEN run.lane = 'USER_IMPORT' THEN 0 ELSE 1 END, job.priority DESC, job.next_attempt_at, job.id LIMIT ? OFFSET ?", AdminPipelineRepository::job, parameters.toArray());
    }

    public List<QueueJobDTO> jobsOfRun(long runId) {
        List<QueueJobDTO> jobs = new ArrayList<>(core.query(JOB_ROWS + " WHERE job.run_id = ? ORDER BY job.id", AdminPipelineRepository::job, runId));
        jobs.addAll(core.query("""
                SELECT song.id, song.youtube_id, song.title, run.lane, queue.attempts, queue.available_at,
                    CASE WHEN song.verification_status <> 'VERIFIED' THEN 'SKIPPED'
                        WHEN queue.song_id IS NOT NULL OR song.embedding IS NULL THEN 'WAITING' ELSE 'DONE' END AS status,
                    ARRAY(SELECT name FROM song_artists WHERE song_id = song.id ORDER BY display_order) AS artist_names
                FROM pipeline_runs run JOIN LATERAL (
                    SELECT known.* FROM songs known WHERE known.youtube_id = run.youtube_id OR EXISTS (
                        SELECT FROM alternate_youtube_ids alternate WHERE alternate.song_id = known.id AND alternate.youtube_id = run.youtube_id)
                    ORDER BY known.id LIMIT 1
                ) song ON true LEFT JOIN song_embedding_queue queue ON queue.song_id = song.id
                WHERE run.id = ? AND run.status = 'DONE'
                """, (row, number) -> new QueueJobDTO(row.getLong("id"), runId, row.getString("youtube_id"), row.getString("title"),
                PipelineLane.valueOf(row.getString("lane")), PipelineStage.EMBEDDING, PipelineJobStatus.valueOf(row.getString("status")),
                row.getInt("attempts") > 0, null, row.getInt("attempts"), null, instant(row, "available_at"), null, null, 0, artists(row)), runId));
        return jobs;
    }

    public String batchLabel(Long batchId) {
        return batchId == null ? null : core.queryForObject("SELECT label FROM import_batches WHERE id = ?", String.class, batchId);
    }

    public List<Long> runIds(PipelineRunStatus status, PipelineLane lane, RunQueueState state, int size, long offset) {
        List<Object> parameters = new ArrayList<>();
        String filter = runFilter(status, lane, parameters);
        if (state != null) { filter += " AND (" + RUN_STATE + ") = ?"; parameters.add(state.name()); }
        parameters.add(size);
        parameters.add(offset);
        return core.queryForList("SELECT run.id FROM pipeline_runs run WHERE true" + filter
                + " ORDER BY run.created_at DESC, run.id DESC LIMIT ? OFFSET ?", Long.class, parameters.toArray());
    }

    public long runCount(PipelineRunStatus status, PipelineLane lane) {
        List<Object> parameters = new ArrayList<>();
        return core.queryForObject("SELECT count(*) FROM pipeline_runs WHERE true" + runFilter(status, lane, parameters),
                Long.class, parameters.toArray());
    }

    public java.util.Map<RunQueueState, Long> runCounts(PipelineRunStatus status, PipelineLane lane) {
        var counts = new java.util.EnumMap<RunQueueState, Long>(RunQueueState.class);
        java.util.Arrays.stream(RunQueueState.values()).forEach(state -> counts.put(state, 0L));
        List<Object> parameters = new ArrayList<>();
        String filter = runFilter(status, lane, parameters);
        core.query("SELECT " + RUN_STATE + " AS queue_state, count(*) AS total FROM pipeline_runs run WHERE true"
                + filter + " GROUP BY queue_state", row -> {
            counts.put(RunQueueState.valueOf(row.getString("queue_state")), row.getLong("total"));
        }, parameters.toArray());
        return counts;
    }

    public List<Long> failedRunIds() {
        return failedRunIds(null);
    }

    public List<Long> failedRunIds(PipelineStage stage) {
        if (stage == null) { return core.queryForList("SELECT id FROM pipeline_runs WHERE status = 'FAILED' ORDER BY id", Long.class); }
        return core.queryForList("""
                SELECT run.id FROM pipeline_runs run WHERE run.status = 'FAILED'
                    AND EXISTS (SELECT FROM pipeline_jobs job WHERE job.run_id = run.id AND job.stage = ? AND job.status = 'FAILED')
                ORDER BY run.id
                """, Long.class, stage.name());
    }

    public long batchCount() { return core.queryForObject("SELECT count(*) FROM import_batches", Long.class); }

    public boolean batchExists(long batchId) {
        return Boolean.TRUE.equals(core.queryForObject("SELECT EXISTS (SELECT FROM import_batches WHERE id = ?)", Boolean.class, batchId));
    }

    public List<ImportBatchDTO> batches(int size, long offset) {
        return core.query("""
                SELECT batch.*, count(run.id) AS total,
                    count(run.id) FILTER (WHERE run.status = 'ACTIVE' AND NOT EXISTS
                        (SELECT FROM pipeline_jobs job WHERE job.run_id = run.id AND job.status = 'IN_PROGRESS')) AS waiting,
                    count(run.id) FILTER (WHERE run.status = 'ACTIVE' AND EXISTS
                        (SELECT FROM pipeline_jobs job WHERE job.run_id = run.id AND job.status = 'IN_PROGRESS')) AS in_progress,
                    count(run.id) FILTER (WHERE run.status = 'DONE') AS done,
                    count(run.id) FILTER (WHERE run.status = 'FAILED') AS failed,
                    count(run.id) FILTER (WHERE run.status = 'CANCELLED') AS cancelled
                FROM import_batches batch LEFT JOIN pipeline_runs run ON run.batch_id = batch.id
                GROUP BY batch.id ORDER BY batch.created_at DESC, batch.id DESC LIMIT ? OFFSET ?
                """, (row, number) -> new ImportBatchDTO(row.getLong("id"), ImportBatchKind.valueOf(row.getString("kind")),
                row.getString("label"), row.getString("source_link"), instant(row, "created_at"), instant(row, "cancelled_at"),
                row.getLong("total"), row.getLong("waiting"), row.getLong("in_progress"), row.getLong("done"),
                row.getLong("failed"), row.getLong("cancelled"), row.getInt("skipped_duplicates"), null), size, offset);
    }

    public List<StageDepth> batchRemainingDepth(long batchId) {
        return core.query("""
                SELECT job.stage, count(*) AS remaining FROM pipeline_jobs job
                JOIN pipeline_runs run ON run.id = job.run_id
                WHERE run.batch_id = ? AND run.status = 'ACTIVE' AND job.status IN ('WAITING', 'IN_PROGRESS')
                GROUP BY job.stage
                """, (row, number) -> new StageDepth(PipelineStage.valueOf(row.getString("stage")), row.getLong("remaining")), batchId);
    }

    private static String runFilter(PipelineRunStatus status, PipelineLane lane, List<Object> parameters) {
        String filter = "";
        if (status != null) { filter += " AND status = ?"; parameters.add(status.name()); }
        if (lane != null) { filter += " AND lane = ?"; parameters.add(lane.name()); }
        return filter;
    }

    // Filters match the stage counts: waiting and retrying cover active runs only, failed covers
    // runs that are still failed, and done today starts at the UTC midnight.
    private static String statusFilter(String status, List<Object> parameters) {
        if (status == null) { return ""; }
        if (AdminPipelineService.DONE_TODAY_FILTER.equals(status)) {
            return " AND job.status = 'DONE' AND job.finished_at >= date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'";
        }
        if (AdminPipelineService.RETRYING_FILTER.equals(status)) { return " AND job.status = 'WAITING' AND job.attempts > 0 AND run.status = 'ACTIVE'"; }
        if (PipelineJobStatus.WAITING.name().equals(status)) { return " AND job.status = 'WAITING' AND run.status = 'ACTIVE'"; }
        if (PipelineJobStatus.FAILED.name().equals(status)) { return " AND job.status = 'FAILED' AND run.status = 'FAILED'"; }
        parameters.add(status);
        return " AND job.status = ?";
    }

    private static final String EMBEDDING_CLAIMED = "(queue.claimed_at IS NOT NULL AND queue.available_at > now())";

    private static String embeddingFilter(String status) {
        if (status == null) { return ""; }
        if (AdminPipelineService.RETRYING_FILTER.equals(status)) { return " WHERE NOT " + EMBEDDING_CLAIMED + " AND queue.attempts > 0"; }
        if (PipelineJobStatus.WAITING.name().equals(status)) { return " WHERE NOT " + EMBEDDING_CLAIMED; }
        return " WHERE " + EMBEDDING_CLAIMED;
    }

    private static boolean embeddingQueueHolds(String status) {
        return status == null || status.equals(PipelineJobStatus.WAITING.name()) || status.equals(AdminPipelineService.RETRYING_FILTER)
                || status.equals(PipelineJobStatus.IN_PROGRESS.name());
    }

    private long embeddingCount(String status) {
        if (!embeddingQueueHolds(status)) { return 0; }
        return core.queryForObject("SELECT count(*) FROM song_embedding_queue queue" + embeddingFilter(status), Long.class);
    }

    // A V41 claim adds to attempts before the work runs, so a claimed row has one try that has not failed.
    private List<QueueJobDTO> embeddingJobs(String status, int size, long offset) {
        if (embeddingCount(status) == 0) { return List.of(); }
        return core.query("""
                SELECT queue.song_id, queue.attempts, queue.available_at, song.youtube_id, song.title,
                    """ + EMBEDDING_CLAIMED + """
                     AS claimed,
                    ARRAY(SELECT name FROM song_artists WHERE song_id = song.id ORDER BY display_order) AS artist_names
                FROM song_embedding_queue queue JOIN songs song ON song.id = queue.song_id
                """ + embeddingFilter(status) + " ORDER BY queue.available_at, queue.song_id LIMIT ? OFFSET ?",
                (row, number) -> {
                    boolean claimed = row.getBoolean("claimed");
                    int failedTries = claimed ? row.getInt("attempts") - CURRENT_TRY : row.getInt("attempts");
                    return new QueueJobDTO(row.getLong("song_id"), null, row.getString("youtube_id"), row.getString("title"),
                            null, PipelineStage.EMBEDDING, claimed ? PipelineJobStatus.IN_PROGRESS : PipelineJobStatus.WAITING,
                            !claimed && failedTries > 0, null, row.getInt("attempts"), null, instant(row, "available_at"), null,
                            null, 0, artists(row), failedTries, null);
                }, size, offset);
    }

    private static QueueJobDTO job(ResultSet row, int number) throws SQLException {
        var status = PipelineJobStatus.valueOf(row.getString("status"));
        int failedTries = row.getInt("attempts");
        boolean failureVisible = PipelineJobFailures.visible(status, failedTries);
        String failureStage = failureVisible ? row.getString("failure_stage") : null;
        return new QueueJobDTO(row.getLong("id"), row.getLong("run_id"), row.getString("youtube_id"), row.getString("title"),
                PipelineLane.valueOf(row.getString("lane")), PipelineStage.valueOf(row.getString("stage")), status,
                status == PipelineJobStatus.WAITING && failedTries > 0, instant(row, "created_at"), failedTries,
                row.getInt("max_attempts"), instant(row, "next_attempt_at"), failureVisible ? row.getString("failure_reason") : null,
                failureStage == null ? null : PipelineStage.valueOf(failureStage), row.getInt("priority"), artists(row),
                failedTries, row.getInt("max_attempts"));
    }

    private static List<String> artists(ResultSet row) throws SQLException {
        var array = row.getArray("artist_names");
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
