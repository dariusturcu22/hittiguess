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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQL for the stage pipeline tables. Every timestamp comes from the caller, so the service's
 * clock alone decides what "now" is. Row locks are taken run first, then job, in every path.
 */
@Repository
public class PipelineJobRepository {

    private static final String VIDEO_LOCK_KEY_PREFIX = "pipeline-run:";
    private static final String ID_COLUMN = "id";
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

    // Serializes run creation for one YouTube ID until the transaction ends.
    public void lockVideo(String youtubeId) {
        core.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) AS acquired", Integer.class,
                VIDEO_LOCK_KEY_PREFIX + youtubeId);
    }

    public Optional<PipelineRun> findActiveRun(String youtubeId) {
        return core.query("SELECT " + RUN_COLUMNS + " FROM pipeline_runs WHERE youtube_id = ? AND status = ?",
                RUN_MAPPER, youtubeId, PipelineRunStatus.ACTIVE.name()).stream().findFirst();
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
        return core.query(LEASE_NEXT_SQL, JOB_MAPPER, stage.name(), timestamp(now),
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
                    AND NOT EXISTS (SELECT FROM stage_controls control
                        WHERE control.stage = job.stage AND control.paused)
                ORDER BY (job.lane = '%3$s') DESC, job.priority DESC, job.created_at, job.id
                LIMIT 1
                FOR UPDATE OF job SKIP LOCKED
            )
            UPDATE pipeline_jobs job
            SET status = ?, lease_owner = ?, lease_expires_at = ?, started_at = COALESCE(job.started_at, ?),
                updated_at = ?
            FROM candidate WHERE job.id = candidate.candidate_id
            RETURNING %4$s
            """.formatted(PipelineJobStatus.WAITING.name(), PipelineRunStatus.ACTIVE.name(),
            PipelineLane.USER_IMPORT.name(), JOB_COLUMNS);

    public void markJobDone(long jobId, String resultJson, Instant now) {
        core.update("""
                UPDATE pipeline_jobs SET status = ?, result = CAST(? AS jsonb), lease_expires_at = NULL,
                    finished_at = ?, updated_at = ?
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

    public void resetFailedJobs(long runId, int maxAttempts, Instant now) {
        core.update("""
                UPDATE pipeline_jobs SET status = ?, attempts = 0, max_attempts = ?, next_attempt_at = ?,
                    lease_owner = NULL, lease_expires_at = NULL, failure_kind = NULL, failure_stage = NULL,
                    failure_reason = NULL, started_at = NULL, finished_at = NULL, updated_at = ?
                WHERE run_id = ? AND status = ?
                """, PipelineJobStatus.WAITING.name(), maxAttempts, timestamp(now), timestamp(now), runId,
                PipelineJobStatus.FAILED.name());
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

    public List<StageCounts> stageCounts(Instant startOfToday) {
        return core.query("""
                SELECT control.stage, control.paused,
                    count(job.id) FILTER (WHERE job.status = ? AND run.status = ?) AS waiting,
                    count(job.id) FILTER (WHERE job.status = ?) AS in_progress,
                    count(job.id) FILTER (WHERE job.status = ? AND job.finished_at >= ?) AS done_today,
                    count(job.id) FILTER (WHERE job.status = ?) AS failed,
                    count(job.id) FILTER (WHERE job.status = ? AND run.status = ? AND job.attempts > 0) AS retrying
                FROM stage_controls control
                LEFT JOIN pipeline_jobs job ON job.stage = control.stage
                LEFT JOIN pipeline_runs run ON run.id = job.run_id
                GROUP BY control.stage, control.paused
                """, (resultSet, rowNumber) -> new StageCounts(
                        PipelineStage.valueOf(resultSet.getString("stage")), resultSet.getBoolean("paused"),
                        resultSet.getLong("waiting"), resultSet.getLong("in_progress"),
                        resultSet.getLong("done_today"), resultSet.getLong("failed"), resultSet.getLong("retrying")),
                PipelineJobStatus.WAITING.name(), PipelineRunStatus.ACTIVE.name(),
                PipelineJobStatus.IN_PROGRESS.name(), PipelineJobStatus.DONE.name(), timestamp(startOfToday),
                PipelineJobStatus.FAILED.name(), PipelineJobStatus.WAITING.name(), PipelineRunStatus.ACTIVE.name());
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
