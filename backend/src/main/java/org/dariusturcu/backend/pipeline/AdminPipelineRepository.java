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
    public record Pace(PipelineStage stage, boolean paused, String reason, Instant cooldownUntil,
                       Integer delayMs, Integer averageMs) { }
    public record StageDepth(PipelineStage stage, long waiting) { }

    private static final String JOB_ROWS = """
            SELECT job.*, run.youtube_id,
                COALESCE(song.title, identification.result ->> 'title', run.youtube_id) AS title
            FROM pipeline_jobs job
            JOIN pipeline_runs run ON run.id = job.run_id
            LEFT JOIN LATERAL (SELECT title FROM songs WHERE youtube_id = run.youtube_id ORDER BY id LIMIT 1) song ON true
            LEFT JOIN pipeline_jobs identification ON identification.run_id = run.id AND identification.stage = 'YOUTUBE_METADATA'
            """;
    private final JdbcTemplate core;

    public AdminPipelineRepository(@Qualifier("coreJdbcTemplate") JdbcTemplate core) { this.core = core; }

    public List<Pace> pace() {
        return core.query("""
                WITH timings AS (
                    SELECT stage, round(avg(EXTRACT(EPOCH FROM finished_at - started_at) * 1000))::int AS average_ms
                    FROM pipeline_jobs WHERE status = 'DONE' AND finished_at >= now() - interval '1 day'
                        AND started_at IS NOT NULL GROUP BY stage
                )
                SELECT control.stage, control.paused, control.paused_reason, pace.cooldown_until,
                    pace.current_delay_ms, COALESCE(pace.avg_response_ms, timings.average_ms) AS avg_response_ms
                FROM stage_controls control LEFT JOIN stage_pace pace ON pace.stage = control.stage
                LEFT JOIN timings ON timings.stage = control.stage ORDER BY control.stage
                """, (row, number) -> new Pace(PipelineStage.valueOf(row.getString("stage")), row.getBoolean("paused"),
                row.getString("paused_reason"), instant(row, "cooldown_until"), row.getObject("current_delay_ms", Integer.class),
                row.getObject("avg_response_ms", Integer.class)));
    }

    public long jobCount(PipelineStage stage, String status) {
        if (stage == PipelineStage.EMBEDDING) { return embeddingCount(status); }
        List<Object> parameters = new ArrayList<>(List.of(stage.name()));
        String filter = statusFilter(status, parameters);
        return core.queryForObject("SELECT count(*) FROM pipeline_jobs job WHERE job.stage = ?" + filter,
                Long.class, parameters.toArray());
    }

    public List<QueueJobDTO> jobs(PipelineStage stage, String status, int size, long offset) {
        if (stage == PipelineStage.EMBEDDING) { return embeddingJobs(status, size, offset); }
        List<Object> parameters = new ArrayList<>(List.of(stage.name()));
        String filter = statusFilter(status, parameters);
        parameters.add(size);
        parameters.add(offset);
        return core.query(JOB_ROWS + " WHERE job.stage = ?" + filter
                + " ORDER BY job.created_at DESC, job.id DESC LIMIT ? OFFSET ?", AdminPipelineRepository::job, parameters.toArray());
    }

    public List<QueueJobDTO> jobsOfRun(long runId) {
        return core.query(JOB_ROWS + " WHERE job.run_id = ? ORDER BY job.id", AdminPipelineRepository::job, runId);
    }

    public List<Long> runIds(PipelineRunStatus status, PipelineLane lane, int size, long offset) {
        List<Object> parameters = new ArrayList<>();
        String filter = runFilter(status, lane, parameters);
        parameters.add(size);
        parameters.add(offset);
        return core.queryForList("SELECT id FROM pipeline_runs WHERE true" + filter
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", Long.class, parameters.toArray());
    }

    public long runCount(PipelineRunStatus status, PipelineLane lane) {
        List<Object> parameters = new ArrayList<>();
        return core.queryForObject("SELECT count(*) FROM pipeline_runs WHERE true" + runFilter(status, lane, parameters),
                Long.class, parameters.toArray());
    }

    public List<Long> failedRunIds() {
        return core.queryForList("SELECT id FROM pipeline_runs WHERE status = 'FAILED' ORDER BY id", Long.class);
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

    public List<StageDepth> batchWaitingDepth(long batchId) {
        return core.query("""
                SELECT job.stage, count(*) AS waiting FROM pipeline_jobs job
                JOIN pipeline_runs run ON run.id = job.run_id
                WHERE run.batch_id = ? AND run.status = 'ACTIVE' AND job.status = 'WAITING' GROUP BY job.stage
                """, (row, number) -> new StageDepth(PipelineStage.valueOf(row.getString("stage")), row.getLong("waiting")), batchId);
    }

    private static String runFilter(PipelineRunStatus status, PipelineLane lane, List<Object> parameters) {
        String filter = "";
        if (status != null) { filter += " AND status = ?"; parameters.add(status.name()); }
        if (lane != null) { filter += " AND lane = ?"; parameters.add(lane.name()); }
        return filter;
    }

    private static String statusFilter(String status, List<Object> parameters) {
        if (status == null) { return ""; }
        if (AdminPipelineService.RETRYING_FILTER.equals(status)) { return " AND job.status = 'WAITING' AND job.attempts > 0"; }
        parameters.add(status);
        return " AND job.status = ?";
    }

    private long embeddingCount(String status) {
        if (status != null && !status.equals(PipelineJobStatus.WAITING.name()) && !status.equals(AdminPipelineService.RETRYING_FILTER)) {
            return 0;
        }
        return core.queryForObject("SELECT count(*) FROM song_embedding_queue"
                + (AdminPipelineService.RETRYING_FILTER.equals(status) ? " WHERE attempts > 0" : ""), Long.class);
    }

    private List<QueueJobDTO> embeddingJobs(String status, int size, long offset) {
        if (embeddingCount(status) == 0) { return List.of(); }
        return core.query("""
                SELECT queue.song_id, queue.attempts, queue.available_at, song.youtube_id, song.title
                FROM song_embedding_queue queue JOIN songs song ON song.id = queue.song_id
                """ + (AdminPipelineService.RETRYING_FILTER.equals(status) ? " WHERE queue.attempts > 0" : "")
                + " ORDER BY queue.available_at, queue.song_id LIMIT ? OFFSET ?",
                (row, number) -> new QueueJobDTO(row.getLong("song_id"), null, row.getString("youtube_id"), row.getString("title"),
                        null, PipelineStage.EMBEDDING, PipelineJobStatus.WAITING, row.getInt("attempts") > 0, null,
                        row.getInt("attempts"), null, instant(row, "available_at"), null, null, 0), size, offset);
    }

    private static QueueJobDTO job(ResultSet row, int number) throws SQLException {
        var status = PipelineJobStatus.valueOf(row.getString("status"));
        String failureStage = row.getString("failure_stage");
        return new QueueJobDTO(row.getLong("id"), row.getLong("run_id"), row.getString("youtube_id"), row.getString("title"),
                PipelineLane.valueOf(row.getString("lane")), PipelineStage.valueOf(row.getString("stage")), status,
                status == PipelineJobStatus.WAITING && row.getInt("attempts") > 0, instant(row, "created_at"), row.getInt("attempts"),
                row.getInt("max_attempts"), instant(row, "next_attempt_at"), row.getString("failure_reason"),
                failureStage == null ? null : PipelineStage.valueOf(failureStage), row.getInt("priority"));
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
