package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SQL for the stage pipeline tables. Every timestamp comes from the caller, so the service's
 * clock alone decides what "now" is. Row locks are taken run first, then job, in every path.
 */
@Repository
public class PipelineJobRepository {

    private static final String VIDEO_LOCK_KEY_PREFIX = "pipeline-run:";
    private static final String ID_COLUMN = "id";
    private static final long NO_FAILURES = 0;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final String RUN_COLUMNS = """
            id, youtube_id, batch_id, lane, status, origin, provisional_year, created_at, ready_to_save_at,
            finished_at""";
    private static final String JOB_COLUMNS = """
            id, run_id, stage, lane, status, attempts, max_attempts, priority, next_attempt_at, lease_owner,
            lease_expires_at, result::text AS result, failure_kind, failure_stage, failure_reason, created_at,
            started_at, finished_at, updated_at""";
    private static final RowMapper<PipelineRun> RUN_MAPPER = PipelineJobRepository::mapRun;
    private static final RowMapper<PipelineJob> JOB_MAPPER = PipelineJobRepository::mapJob;

    private final JdbcTemplate core;

    public PipelineJobRepository(@Qualifier("coreJdbcTemplate") JdbcTemplate core) {
        this.core = core;
    }

    public long insertBatch(ImportBatchKind kind, String label, String sourceLink, Long createdBy, Instant now) {
        return insertReturningId("""
                INSERT INTO import_batches (kind, label, source_link, created_by, created_at) VALUES (?, ?, ?, ?, ?)
                """, kind.name(), label, sourceLink, createdBy, timestamp(now));
    }

    public void markBatchCancelled(long batchId, Instant now) {
        core.update("UPDATE import_batches SET cancelled_at = ? WHERE id = ? AND cancelled_at IS NULL",
                timestamp(now), batchId);
    }

    public void recordSkippedDuplicates(long batchId, int count) {
        core.update("UPDATE import_batches SET skipped_duplicates = ? WHERE id = ?", count, batchId);
    }

    public boolean lockCancelledBatch(long batchId) {
        return Boolean.TRUE.equals(core.queryForObject(
                "SELECT cancelled_at IS NOT NULL FROM import_batches WHERE id = ? FOR UPDATE", Boolean.class, batchId));
    }

    // Serializes run creation for one YouTube ID until the transaction ends.
    public void lockVideo(String youtubeId) {
        core.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) AS acquired", Integer.class,
                VIDEO_LOCK_KEY_PREFIX + youtubeId);
    }

    public Optional<PipelineRun> findActiveRun(String youtubeId) {
        return core.query("SELECT " + RUN_COLUMNS + " FROM pipeline_runs WHERE youtube_id = ? AND status = ?",
                RUN_MAPPER, youtubeId, PipelineRunStatus.ACTIVE.name()).stream().findFirst();
    }

    public PipelineRun promoteToUserLane(PipelineRun run, PendingImportOrigin origin, Integer provisionalYear) {
        core.update("UPDATE pipeline_runs SET lane = ?, origin = ?, provisional_year = ? WHERE id = ?",
                PipelineLane.USER_IMPORT.name(), origin.name(), provisionalYear, run.id());
        core.update("UPDATE pipeline_jobs SET lane = ? WHERE run_id = ?",
                PipelineLane.USER_IMPORT.name(), run.id());
        return findRun(run.id()).orElseThrow();
    }

    public PipelineRun insertRun(String youtubeId, PipelineLane lane, PendingImportOrigin origin, Long batchId,
                                 Integer provisionalYear, Instant now) {
        long runId = insertReturningId("""
                INSERT INTO pipeline_runs (youtube_id, batch_id, lane, status, origin, provisional_year, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, youtubeId, batchId, lane.name(), PipelineRunStatus.ACTIVE.name(), origin.name(), provisionalYear,
                timestamp(now));
        return findRun(runId).orElseThrow();
    }

    public Optional<PipelineRun> findRun(long runId) {
        return core.query("SELECT " + RUN_COLUMNS + " FROM pipeline_runs WHERE id = ?", RUN_MAPPER, runId)
                .stream().findFirst();
    }

    public Optional<PipelineRun> lockRun(long runId) {
        return core.query("SELECT " + RUN_COLUMNS + " FROM pipeline_runs WHERE id = ? FOR UPDATE", RUN_MAPPER, runId)
                .stream().findFirst();
    }

    public List<Long> lockActiveRunsOfBatch(long batchId) {
        return core.queryForList("SELECT id FROM pipeline_runs WHERE batch_id = ? AND status = ? ORDER BY id FOR UPDATE",
                Long.class, batchId, PipelineRunStatus.ACTIVE.name());
    }

    public void markRunReadyToSave(long runId, Instant now) {
        core.update("UPDATE pipeline_runs SET ready_to_save_at = ? WHERE id = ? AND ready_to_save_at IS NULL",
                timestamp(now), runId);
    }

    public void finishRun(long runId, PipelineRunStatus status, Instant now) {
        core.update("UPDATE pipeline_runs SET status = ?, finished_at = ? WHERE id = ?",
                status.name(), timestamp(now), runId);
    }

    public void reactivateRun(long runId) {
        core.update("UPDATE pipeline_runs SET status = ?, finished_at = NULL WHERE id = ?",
                PipelineRunStatus.ACTIVE.name(), runId);
    }

    public void insertJob(long runId, PipelineStage stage, PipelineLane lane, PipelineJobStatus status,
                          int priority, int maxAttempts, Instant now) {
        Instant finishedAt = status.isTerminal() ? now : null;
        core.update("""
                INSERT INTO pipeline_jobs (run_id, stage, lane, status, priority, max_attempts, next_attempt_at,
                    created_at, finished_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (run_id, stage) DO NOTHING
                """, runId, stage.name(), lane.name(), status.name(), priority, maxAttempts, timestamp(now),
                timestamp(now), timestamp(finishedAt), timestamp(now));
    }

    public Optional<PipelineJob> findJob(long jobId) {
        return core.query("SELECT " + JOB_COLUMNS + " FROM pipeline_jobs WHERE id = ?", JOB_MAPPER, jobId)
                .stream().findFirst();
    }

    public Optional<PipelineJob> lockJob(long jobId) {
        return core.query("SELECT " + JOB_COLUMNS + " FROM pipeline_jobs WHERE id = ? FOR UPDATE", JOB_MAPPER, jobId)
                .stream().findFirst();
    }

    public List<PipelineJob> jobsOfRun(long runId) {
        return core.query("SELECT " + JOB_COLUMNS + " FROM pipeline_jobs WHERE run_id = ? ORDER BY id",
                JOB_MAPPER, runId);
    }

    // A result's earliestYear counts only when it is a JSON number; anything else reads as no year.
    public Map<PipelineStage, StageDependencies.StageJobState> jobStatesOfRun(long runId) {
        Map<PipelineStage, StageDependencies.StageJobState> statesByStage = new EnumMap<>(PipelineStage.class);
        core.query("""
                SELECT stage, status,
                    CASE WHEN jsonb_typeof(result -> 'earliestYear') = 'number'
                        THEN (result ->> 'earliestYear')::numeric::int END AS earliest_year
                FROM pipeline_jobs WHERE run_id = ?
                """, resultSet -> {
            statesByStage.put(PipelineStage.valueOf(resultSet.getString("stage")),
                    new StageDependencies.StageJobState(PipelineJobStatus.valueOf(resultSet.getString("status")),
                            resultSet.getObject("earliest_year", Integer.class)));
        }, runId);
        return statesByStage;
    }

    public List<Long> expiredLeaseJobIds(PipelineStage stage, Instant now) {
        return core.queryForList("""
                SELECT id FROM pipeline_jobs
                WHERE stage = ? AND status = ? AND lease_expires_at < ?
                ORDER BY run_id, id
                """, Long.class, stage.name(), PipelineJobStatus.IN_PROGRESS.name(), timestamp(now));
    }

    public Optional<PipelineJob> leaseNextWaiting(PipelineStage stage, String leaseOwner, Instant leaseExpiresAt,
                                                  Instant now) {
        return leaseNextWaiting(stage, leaseOwner, leaseExpiresAt, now, true);
    }

    public boolean hasDueWork(PipelineStage stage, Instant now) {
        return Boolean.TRUE.equals(core.queryForObject("""
                SELECT EXISTS (SELECT FROM pipeline_jobs job
                    JOIN pipeline_runs run ON run.id = job.run_id
                    WHERE job.stage = ? AND run.status = 'ACTIVE'
                        AND ((job.status = 'WAITING' AND job.next_attempt_at <= ?)
                            OR (job.status = 'IN_PROGRESS' AND job.lease_expires_at < ?))
                        AND NOT EXISTS (SELECT FROM stage_controls WHERE stage = job.stage AND paused))
                """, Boolean.class, stage.name(), timestamp(now), timestamp(now)));
    }

    // One indexed read for every stage, so an idle poll costs a single round trip.
    public Set<PipelineStage> stagesWithDueWork(Instant now) {
        Set<PipelineStage> dueStages = EnumSet.noneOf(PipelineStage.class);
        core.query("""
                SELECT DISTINCT job.stage FROM pipeline_jobs job
                JOIN pipeline_runs run ON run.id = job.run_id
                WHERE job.status IN ('WAITING', 'IN_PROGRESS') AND run.status = 'ACTIVE'
                    AND ((job.status = 'WAITING' AND job.next_attempt_at <= ?)
                        OR (job.status = 'IN_PROGRESS' AND job.lease_expires_at < ?))
                    AND NOT EXISTS (SELECT FROM stage_controls WHERE stage = job.stage AND paused)
                """, row -> {
            dueStages.add(PipelineStage.valueOf(row.getString("stage")));
        }, timestamp(now), timestamp(now));
        return dueStages;
    }

    // Holds the stage control row lock until the transaction ends, so the count stays true for the lease that follows.
    public long lockStageAndCountInProgress(PipelineStage stage) {
        core.queryForObject("SELECT stage FROM stage_controls WHERE stage = ? FOR UPDATE", String.class, stage.name());
        return core.queryForObject("SELECT count(*) FROM pipeline_jobs WHERE stage = ? AND status = 'IN_PROGRESS'",
                Long.class, stage.name());
    }

    public Optional<PipelineJob> leaseNextWaiting(PipelineStage stage, String leaseOwner, Instant leaseExpiresAt,
                                                  Instant now, boolean allowBackfill) {
        return core.query(LEASE_NEXT_SQL, JOB_MAPPER, stage.name(), timestamp(now), allowBackfill,
                PipelineJobStatus.IN_PROGRESS.name(), leaseOwner, timestamp(leaseExpiresAt), timestamp(now),
                timestamp(now)).stream().findFirst();
    }

    // The waiting status and the user-import lane are literals, not parameters, so the planner can
    // match the partial lease-order index on every execution, including generic prepared plans.
    static final String LEASE_NEXT_SQL = """
            WITH candidate AS (
                SELECT job.id AS candidate_id FROM pipeline_jobs job
                JOIN pipeline_runs run ON run.id = job.run_id
                WHERE job.stage = ? AND job.status = '%1$s' AND job.next_attempt_at <= ? AND run.status = '%2$s'
                    AND (? OR job.lane = '%3$s')
                    AND NOT EXISTS (SELECT FROM stage_controls control
                        WHERE control.stage = job.stage AND control.paused)
                ORDER BY (job.lane = '%3$s') DESC, job.priority DESC, job.created_at, job.id
                LIMIT 1
                FOR UPDATE OF job SKIP LOCKED
            )
            UPDATE pipeline_jobs job
            SET status = ?, lease_owner = ?, lease_expires_at = ?, started_at = ?, updated_at = ?
            FROM candidate WHERE job.id = candidate.candidate_id
            RETURNING %4$s
            """.formatted(PipelineJobStatus.WAITING.name(), PipelineRunStatus.ACTIVE.name(),
            PipelineLane.USER_IMPORT.name(), JOB_COLUMNS);

    public void markJobDone(long jobId, String resultJson, Instant now) {
        core.update("""
                UPDATE pipeline_jobs SET status = ?, result = CAST(? AS jsonb), lease_expires_at = NULL,
                    failure_kind = NULL, failure_stage = NULL, failure_reason = NULL, finished_at = ?, updated_at = ?
                WHERE id = ?
                """, PipelineJobStatus.DONE.name(), resultJson, timestamp(now), timestamp(now), jobId);
    }

    public void scheduleRetry(long jobId, int attempts, Instant nextAttemptAt, String reason, Instant now) {
        core.update("""
                UPDATE pipeline_jobs SET status = ?, attempts = ?, next_attempt_at = ?, lease_owner = NULL,
                    lease_expires_at = NULL, failure_kind = ?, failure_stage = stage, failure_reason = ?,
                    updated_at = ?
                WHERE id = ?
                """, PipelineJobStatus.WAITING.name(), attempts, timestamp(nextAttemptAt), FailureKind.TRANSIENT.name(),
                reason, timestamp(now), jobId);
    }

    public List<PipelineStage> retryWaitingJobs(long runId, Instant now) {
        return core.query("""
                UPDATE pipeline_jobs SET next_attempt_at = ?, updated_at = ?
                WHERE run_id = ? AND status = 'WAITING' AND failure_kind = 'TRANSIENT'
                RETURNING stage
                """, (row, number) -> PipelineStage.valueOf(row.getString("stage")), timestamp(now), timestamp(now), runId);
    }

    public void markJobFailed(long jobId, int attempts, FailureKind failureKind, String reason, Instant now) {
        core.update("""
                UPDATE pipeline_jobs SET status = ?, attempts = ?, lease_expires_at = NULL, failure_kind = ?,
                    failure_stage = stage, failure_reason = ?, finished_at = ?, updated_at = ?
                WHERE id = ?
                """, PipelineJobStatus.FAILED.name(), attempts, failureKind.name(), reason, timestamp(now),
                timestamp(now), jobId);
    }

    public void markJobCancelled(long jobId, Instant now) {
        core.update("""
                UPDATE pipeline_jobs SET status = ?, lease_expires_at = NULL, finished_at = ?, updated_at = ?
                WHERE id = ?
                """, PipelineJobStatus.CANCELLED.name(), timestamp(now), timestamp(now), jobId);
    }

    public void cancelWaitingJobsOfRuns(List<Long> runIds, Instant now) {
        for (Long runId : runIds) {
            core.update("""
                    UPDATE pipeline_jobs SET status = ?, finished_at = ?, updated_at = ?
                    WHERE run_id = ? AND status = ?
                    """, PipelineJobStatus.CANCELLED.name(), timestamp(now), timestamp(now), runId,
                    PipelineJobStatus.WAITING.name());
        }
    }

    public List<PipelineStage> resetFailedJobs(long runId, int maxAttempts, Instant now) {
        return core.query("""
                UPDATE pipeline_jobs SET status = ?, attempts = 0, max_attempts = ?, next_attempt_at = ?,
                    lease_owner = NULL, lease_expires_at = NULL, failure_kind = NULL, failure_stage = NULL,
                    failure_reason = NULL, started_at = NULL, finished_at = NULL, updated_at = ?
                WHERE run_id = ? AND status = ?
                RETURNING stage
                """, (row, number) -> PipelineStage.valueOf(row.getString("stage")), PipelineJobStatus.WAITING.name(),
                maxAttempts, timestamp(now), timestamp(now), runId, PipelineJobStatus.FAILED.name());
    }

    // Moves the run's unfinished jobs above every job now waiting; jobs the run creates later
    // inherit that priority from the job that finished before them.
    public int raisePriorityAboveWaiting(long runId, Instant now) {
        return core.update("""
                UPDATE pipeline_jobs
                SET priority = (SELECT COALESCE(MAX(priority), 0) + 1 FROM pipeline_jobs WHERE status = ?),
                    updated_at = ?
                WHERE run_id = ? AND status IN (?, ?)
                """, PipelineJobStatus.WAITING.name(), timestamp(now), runId, PipelineJobStatus.WAITING.name(),
                PipelineJobStatus.IN_PROGRESS.name());
    }

    public void setStagePaused(PipelineStage stage, boolean paused, String reason, Instant now) {
        core.update("UPDATE stage_controls SET paused = ?, paused_reason = ?, paused_at = ? WHERE stage = ?",
                paused, paused ? reason : null, paused ? timestamp(now) : null, stage.name());
    }

    // An in-progress job whose lease expired counts as queued again: the next lease reclaims it as a failed try.
    public List<StageCounts> stageCounts(Instant now, Instant startOfToday) {
        return core.query("""
                WITH classified AS (
                    SELECT job.stage, job.status, job.attempts, job.next_attempt_at, job.finished_at,
                        run.status AS run_status,
                        job.status = 'IN_PROGRESS' AND job.lease_expires_at < ? AS lease_expired
                    FROM pipeline_jobs job JOIN pipeline_runs run ON run.id = job.run_id
                    WHERE job.status IN ('WAITING', 'IN_PROGRESS', 'FAILED')
                        OR (job.status = 'DONE' AND job.finished_at >= ?)
                )
                SELECT control.stage, control.paused,
                    count(classified.stage) FILTER (WHERE classified.run_status = 'ACTIVE'
                        AND (classified.status = 'WAITING' OR classified.lease_expired)) AS waiting,
                    count(classified.stage) FILTER (WHERE classified.run_status = 'ACTIVE'
                        AND ((classified.status = 'WAITING' AND classified.next_attempt_at <= ?)
                            OR classified.lease_expired)) AS waiting_due,
                    count(classified.stage) FILTER (WHERE classified.status = 'IN_PROGRESS'
                        AND NOT classified.lease_expired) AS in_progress,
                    count(classified.stage) FILTER (WHERE classified.status = 'DONE') AS done_today,
                    count(classified.stage) FILTER (WHERE classified.status = 'FAILED'
                        AND classified.run_status = 'FAILED') AS failed,
                    count(classified.stage) FILTER (WHERE classified.run_status = 'ACTIVE'
                        AND ((classified.status = 'WAITING' AND classified.attempts > 0)
                            OR classified.lease_expired)) AS retrying
                FROM stage_controls control
                LEFT JOIN classified ON classified.stage = control.stage
                GROUP BY control.stage, control.paused
                """, (resultSet, rowNumber) -> new StageCounts(
                        PipelineStage.valueOf(resultSet.getString("stage")), resultSet.getBoolean("paused"),
                        resultSet.getLong("waiting"), resultSet.getLong("in_progress"),
                        resultSet.getLong("done_today"), resultSet.getLong("failed"), resultSet.getLong("retrying"),
                        resultSet.getLong("waiting_due")),
                timestamp(now), timestamp(startOfToday), timestamp(now));
    }

    /** V41 retries indefinitely; claimed rows count as in progress until their lease expires. */
    public StageCounts embeddingCounts(Instant now, Instant startOfToday) {
        return core.queryForObject("""
                SELECT (SELECT paused FROM stage_controls WHERE stage = 'EMBEDDING') AS paused,
                    count(*) FILTER (WHERE claimed_at IS NULL OR available_at <= ?) AS waiting,
                    count(*) FILTER (WHERE available_at <= ?) AS waiting_due,
                    count(*) FILTER (WHERE claimed_at IS NOT NULL AND available_at > ?) AS in_progress,
                    count(*) FILTER (WHERE (claimed_at IS NULL OR available_at <= ?) AND attempts > 0) AS retrying,
                    (SELECT count(*) FROM songs WHERE embedding_stored_at >= ? AND embedding IS NOT NULL) AS done_today
                FROM song_embedding_queue
                """, (row, number) -> new StageCounts(PipelineStage.EMBEDDING, row.getBoolean("paused"),
                row.getLong("waiting"), row.getLong("in_progress"), row.getLong("done_today"), NO_FAILURES,
                row.getLong("retrying"), row.getLong("waiting_due")),
                timestamp(now), timestamp(now), timestamp(now), timestamp(now), timestamp(startOfToday));
    }

    /** Timings of every metadata stage from jobs that finished done since the given instants. */
    public Map<PipelineStage, StageTimings> stageTimings(Instant durationWindowStart, Instant throughputWindowStart) {
        Map<PipelineStage, StageTimings> timingsByStage = new EnumMap<>(PipelineStage.class);
        Instant earliestStart = durationWindowStart.isBefore(throughputWindowStart) ? durationWindowStart : throughputWindowStart;
        core.query("""
                SELECT stage,
                    round(avg(EXTRACT(EPOCH FROM finished_at - started_at) * %1$d)
                        FILTER (WHERE finished_at >= ?))::bigint AS average_duration_ms,
                    round(avg(GREATEST(EXTRACT(EPOCH FROM started_at - next_attempt_at), 0) * %1$d)
                        FILTER (WHERE finished_at >= ?))::bigint AS average_wait_ms,
                    count(*) FILTER (WHERE finished_at >= ?) AS finished_in_duration_window,
                    count(*) FILTER (WHERE finished_at >= ?) AS finished_in_throughput_window
                FROM pipeline_jobs
                WHERE status = 'DONE' AND started_at IS NOT NULL AND finished_at >= ?
                GROUP BY stage
                """.formatted(MILLISECONDS_PER_SECOND), row -> {
            PipelineStage stage = PipelineStage.valueOf(row.getString("stage"));
            timingsByStage.put(stage, new StageTimings(stage, row.getObject("average_duration_ms", Long.class),
                    row.getObject("average_wait_ms", Long.class), row.getLong("finished_in_duration_window"),
                    row.getLong("finished_in_throughput_window")));
        }, timestamp(durationWindowStart), timestamp(durationWindowStart), timestamp(durationWindowStart),
                timestamp(throughputWindowStart), timestamp(earliestStart));
        return timingsByStage;
    }

    /** Embedding timings from stored embeddings; songs embedded before V47 carry no times. */
    public StageTimings embeddingTimings(Instant durationWindowStart, Instant throughputWindowStart) {
        return core.queryForObject("""
                SELECT round(avg(EXTRACT(EPOCH FROM embedding_stored_at - embedding_started_at) * %1$d)
                        FILTER (WHERE embedding_started_at IS NOT NULL))::bigint AS average_duration_ms,
                    count(*) AS finished_in_duration_window,
                    count(*) FILTER (WHERE embedding_stored_at >= ?) AS finished_in_throughput_window
                FROM songs WHERE embedding IS NOT NULL AND embedding_stored_at >= ?
                """.formatted(MILLISECONDS_PER_SECOND), (row, number) -> new StageTimings(PipelineStage.EMBEDDING, row.getObject("average_duration_ms", Long.class),
                null, row.getLong("finished_in_duration_window"), row.getLong("finished_in_throughput_window")),
                timestamp(throughputWindowStart), timestamp(durationWindowStart));
    }

    private long insertReturningId(String sql, Object... arguments) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        core.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{ID_COLUMN});
            new ArgumentPreparedStatementSetter(arguments).setValues(statement);
            return statement;
        }, keyHolder);
        return keyHolder.getKeyAs(Long.class);
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    private static PipelineRun mapRun(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PipelineRun(
                resultSet.getLong("id"),
                resultSet.getString("youtube_id"),
                resultSet.getObject("batch_id", Long.class),
                PipelineLane.valueOf(resultSet.getString("lane")),
                PipelineRunStatus.valueOf(resultSet.getString("status")),
                PendingImportOrigin.valueOf(resultSet.getString("origin")),
                resultSet.getObject("provisional_year", Integer.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "ready_to_save_at"),
                instant(resultSet, "finished_at"));
    }

    private static PipelineJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PipelineJob(
                resultSet.getLong("id"),
                resultSet.getLong("run_id"),
                PipelineStage.valueOf(resultSet.getString("stage")),
                PipelineLane.valueOf(resultSet.getString("lane")),
                PipelineJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempts"),
                resultSet.getInt("max_attempts"),
                resultSet.getInt("priority"),
                instant(resultSet, "next_attempt_at"),
                resultSet.getString("lease_owner"),
                instant(resultSet, "lease_expires_at"),
                resultSet.getString("result"),
                enumOrNull(FailureKind.class, resultSet.getString("failure_kind")),
                enumOrNull(PipelineStage.class, resultSet.getString("failure_stage")),
                resultSet.getString("failure_reason"),
                instant(resultSet, "created_at"),
                instant(resultSet, "started_at"),
                instant(resultSet, "finished_at"),
                instant(resultSet, "updated_at"));
    }
}
