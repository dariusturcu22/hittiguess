package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.scheduling.PipelineRetentionSweeper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PipelineRetentionIntegrationTest {

    private static final int RETENTION_DAYS = 30;
    private static final int STALE_RUN_AGE_DAYS = 40;
    private static final int RECENT_RUN_AGE_DAYS = 5;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static JdbcTemplate jdbcTemplate;
    private static PipelineJobRepository repository;
    private static PipelineRetentionService retentionService;
    private static PipelineRetentionSweeper retentionSweeper;

    @BeforeAll
    static void setupDatabase() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new PipelineJobRepository(jdbcTemplate);
        retentionService = new PipelineRetentionService(repository, RETENTION_DAYS);
        retentionSweeper = new PipelineRetentionSweeper(retentionService);
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("DELETE FROM pipeline_jobs");
        jdbcTemplate.update("DELETE FROM pipeline_runs");
        jdbcTemplate.update("DELETE FROM import_batches");
    }

    @Test
    void purgesTerminalRunsAndTheirJobsOlderThanRetentionCutoffWhileKeepingActiveAndRecentRuns() {
        Instant staleFinishTime = Instant.now().minus(STALE_RUN_AGE_DAYS, ChronoUnit.DAYS);
        Instant recentFinishTime = Instant.now().minus(RECENT_RUN_AGE_DAYS, ChronoUnit.DAYS);

        long staleDoneRunId = insertRun("videoOldDone", "DONE", staleFinishTime);
        insertJob(staleDoneRunId, "YOUTUBE_METADATA", "DONE", staleFinishTime);

        long staleFailedRunId = insertRun("videoOldFailed", "FAILED", staleFinishTime);
        insertJob(staleFailedRunId, "MUSICBRAINZ", "FAILED", staleFinishTime);

        long staleActiveRunId = insertRun("videoOldActive", "ACTIVE", null);
        insertJob(staleActiveRunId, "DISCOGS", "WAITING", null);

        long recentDoneRunId = insertRun("videoRecentDone", "DONE", recentFinishTime);
        insertJob(recentDoneRunId, "WIKIDATA", "DONE", recentFinishTime);

        int totalPurged = retentionService.purgeTerminalPipelineRunsAndJobs();

        assertThat(totalPurged).isEqualTo(2);

        Integer remainingRunsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_runs", Integer.class);
        assertThat(remainingRunsCount).isEqualTo(2);

        Integer remainingJobsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_jobs", Integer.class);
        assertThat(remainingJobsCount).isEqualTo(2);

        Integer staleDoneRemaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_runs WHERE id = ?", Integer.class, staleDoneRunId);
        assertThat(staleDoneRemaining).isZero();

        Integer staleFailedRemaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_runs WHERE id = ?", Integer.class, staleFailedRunId);
        assertThat(staleFailedRemaining).isZero();

        Integer staleActiveRemaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_runs WHERE id = ?", Integer.class, staleActiveRunId);
        assertThat(staleActiveRemaining).isEqualTo(1);

        Integer recentDoneRemaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_runs WHERE id = ?", Integer.class, recentDoneRunId);
        assertThat(recentDoneRemaining).isEqualTo(1);
    }

    @Test
    void sweeperExecutesSuccessfullyWithoutError() {
        Instant staleFinishTime = Instant.now().minus(STALE_RUN_AGE_DAYS, ChronoUnit.DAYS);
        long runId = insertRun("videoOldSweeper", "DONE", staleFinishTime);
        insertJob(runId, "YOUTUBE_METADATA", "DONE", staleFinishTime);

        retentionSweeper.sweep();

        Integer remainingRunsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_runs", Integer.class);
        assertThat(remainingRunsCount).isZero();
    }

    private long insertRun(String youtubeId, String status, Instant finishedAt) {
        Timestamp finishedTimestamp = finishedAt == null ? null : Timestamp.from(finishedAt);
        return jdbcTemplate.queryForObject("""
                INSERT INTO pipeline_runs (youtube_id, lane, status, origin, created_at, finished_at)
                VALUES (?, 'USER_IMPORT', ?, 'ADMIN_SEED', now(), ?)
                RETURNING id
                """, Long.class, youtubeId, status, finishedTimestamp);
    }

    private void insertJob(long runId, String stage, String status, Instant finishedAt) {
        Timestamp finishedTimestamp = finishedAt == null ? null : Timestamp.from(finishedAt);
        jdbcTemplate.update("""
                INSERT INTO pipeline_jobs (run_id, stage, lane, status, max_attempts, next_attempt_at, created_at, finished_at, updated_at)
                VALUES (?, ?, 'USER_IMPORT', ?, ?, now(), now(), ?, now())
                """, runId, stage, status, DEFAULT_MAX_ATTEMPTS, finishedTimestamp);
    }
}
