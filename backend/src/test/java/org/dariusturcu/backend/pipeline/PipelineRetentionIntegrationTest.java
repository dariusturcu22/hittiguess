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
        jdbcTemplate.update("DELETE FROM playlist_import_job_items");
        jdbcTemplate.update("DELETE FROM playlist_import_jobs");
        jdbcTemplate.update("DELETE FROM pipeline_jobs");
        jdbcTemplate.update("DELETE FROM pipeline_runs");
        jdbcTemplate.update("DELETE FROM import_batches");
        jdbcTemplate.update("DELETE FROM playlists");
        jdbcTemplate.update("DELETE FROM users");
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
    void keepsFinishedStageJobsOfARunThatIsStillActive() {
        Instant staleFinishTime = Instant.now().minus(STALE_RUN_AGE_DAYS, ChronoUnit.DAYS);
        long activeRunId = insertRun("videoActiveWithOldStage", "ACTIVE", null);
        insertJob(activeRunId, "YOUTUBE_METADATA", "DONE", staleFinishTime);
        insertJob(activeRunId, "MUSICBRAINZ", "WAITING", null);

        retentionService.purgeTerminalPipelineRunsAndJobs();

        Integer remainingJobsOfActiveRun = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pipeline_jobs WHERE run_id = ?", Integer.class, activeRunId);
        assertThat(remainingJobsOfActiveRun).isEqualTo(2);
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

    @Test
    void purgesTerminalPlaylistImportJobsAndTheirRawMetadataItemsOlderThanRetentionCutoff() {
        long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (username, password, email, role, email_verified, created_at)
                VALUES ('testowner', 'hash', 'testowner@example.com', 'USER', true, now())
                RETURNING id
                """, Long.class);

        long playlistId = jdbcTemplate.queryForObject("""
                INSERT INTO playlists (name, color, invite_code, owner_id)
                VALUES ('Test Playlist', '123456', 'invite-retention-test', ?)
                RETURNING id
                """, Long.class, userId);

        Instant staleCompletedAt = Instant.now().minus(STALE_RUN_AGE_DAYS, ChronoUnit.DAYS);
        Instant recentCompletedAt = Instant.now().minus(RECENT_RUN_AGE_DAYS, ChronoUnit.DAYS);

        insertPlaylistImportJob("stale-done-job", playlistId, "DONE", staleCompletedAt, staleCompletedAt);
        insertPlaylistImportJobItem("stale-done-job", "video1", "Raw Video 1", "Raw Channel 1");

        insertPlaylistImportJob("stale-failed-job", playlistId, "FAILED", staleCompletedAt, null);
        insertPlaylistImportJobItem("stale-failed-job", "video2", "Raw Video 2", "Raw Channel 2");

        insertPlaylistImportJob("stale-running-job", playlistId, "RUNNING", staleCompletedAt, null);
        insertPlaylistImportJobItem("stale-running-job", "video3", "Raw Video 3", "Raw Channel 3");

        insertPlaylistImportJob("recent-done-job", playlistId, "DONE", recentCompletedAt, recentCompletedAt);
        insertPlaylistImportJobItem("recent-done-job", "video4", "Raw Video 4", "Raw Channel 4");

        retentionService.purgeTerminalPipelineRunsAndJobs();

        Integer remainingJobs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM playlist_import_jobs", Integer.class);
        assertThat(remainingJobs).isEqualTo(2);

        Integer remainingItems = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM playlist_import_job_items", Integer.class);
        assertThat(remainingItems).isEqualTo(2);

        Integer staleCompletedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM playlist_import_jobs WHERE id = 'stale-done-job'", Integer.class);
        assertThat(staleCompletedCount).isZero();

        Integer staleFailedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM playlist_import_jobs WHERE id = 'stale-failed-job'", Integer.class);
        assertThat(staleFailedCount).isZero();

        Integer staleRunningCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM playlist_import_jobs WHERE id = 'stale-running-job'", Integer.class);
        assertThat(staleRunningCount).isEqualTo(1);

        Integer recentCompletedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM playlist_import_jobs WHERE id = 'recent-done-job'", Integer.class);
        assertThat(recentCompletedCount).isEqualTo(1);
    }

    private void insertPlaylistImportJob(String jobId, long playlistId, String status, Instant createdAt, Instant completedAt) {
        Timestamp createdTimestamp = Timestamp.from(createdAt);
        Timestamp completedTimestamp = completedAt == null ? null : Timestamp.from(completedAt);
        jdbcTemplate.update("""
                INSERT INTO playlist_import_jobs (id, playlist_id, submitted_by_username, status, created_at, completed_at)
                VALUES (?, ?, 'testuser', ?, ?, ?)
                """, jobId, playlistId, status, createdTimestamp, completedTimestamp);
    }

    private void insertPlaylistImportJobItem(String jobId, String youtubeId, String rawTitle, String rawChannelTitle) {
        jdbcTemplate.update("""
                INSERT INTO playlist_import_job_items (job_id, youtube_id, status, raw_title, raw_channel_title)
                VALUES (?, ?, 'RESOLVED', ?, ?)
                """, jobId, youtubeId, rawTitle, rawChannelTitle);
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
