package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.admin.UpdateCatalogSongRequest;
import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.service.ApiUsageService;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.dariusturcu.backend.service.MetadataPriorityCoordinator;
import org.dariusturcu.backend.service.PendingImportProcessor;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.YoutubeIdLookupService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class AdminPipelineIntegrationTest {
    private static final String VIDEO = "adminvideo1";
    private static final String OTHER_VIDEO = "adminvideo2";
    private static final String OWNER = "control-tests";
    private static final int YEAR = 1999;
    private static final int UPDATED_YEAR = 2001;
    private static final int EXTRA_PAGE_ITEMS = 3;
    private static final long AVAILABLE_UNITS = 10_000;
    private static final int SOURCE_DELAY_MS = 1500;
    private static final int SOURCE_RESPONSE_MS = 250;
    private static final long COOLDOWN_SECONDS = java.time.Duration.ofHours(1).toSeconds();
    private static final long LEGACY_DRAIN_QUOTA = 200;
    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final double EMBEDDING_COMPONENT = 0.1;
    private static final int JOB_DURATION_MS = 20_000;
    private static final int QUEUE_WAIT_MS = 5_000;
    private static final String STALE_REASON = "DEEPINFRA HTTP_ERROR (HTTP 429)";
    private static final PipelineSettings SETTINGS = new PipelineSettings(true, PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");
    private static JdbcTemplate core;
    private static JdbcTransactionManager transactions;
    private PipelineJobService engine;
    private AdminPipelineRepository repository;
    private AdminPipelineService admin;
    private AdminSongPipelineService songs;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        core = new JdbcTemplate(dataSource);
        transactions = new JdbcTransactionManager(dataSource);
    }

    @BeforeEach
    void reset() {
        core.execute("TRUNCATE songs, pipeline_runs, import_batches, pending_imports, users, playlists, groups RESTART IDENTITY CASCADE");
        core.update("UPDATE stage_controls SET paused = false, paused_reason = NULL, paused_at = NULL");
        core.update("UPDATE stage_pace SET cooldown_until = NULL, current_delay_ms = ?, avg_response_ms = ?",
                SOURCE_DELAY_MS, SOURCE_RESPONSE_MS);
        var jobRepository = new PipelineJobRepository(core);
        engine = new PipelineJobService(jobRepository, transactions, PipelineJobService.DEFAULT_MAX_ATTEMPTS);
        repository = new AdminPipelineRepository(core);
        ApiUsageService usage = mock(ApiUsageService.class);
        when(usage.youtubeUnitsRemaining()).thenReturn(AVAILABLE_UNITS);
        var lookup = mock(YoutubeIdLookupService.class);
        when(lookup.partitionKnownAndUnknown(anyCollection())).thenAnswer(invocation -> {
            Collection<String> submitted = invocation.getArgument(0);
            Set<String> known = new LinkedHashSet<>(core.queryForList("SELECT youtube_id FROM songs", String.class));
            known.retainAll(submitted);
            Set<String> unknown = new LinkedHashSet<>(submitted); unknown.removeAll(known);
            return new YoutubeIdLookupResult(known, unknown);
        });
        var expansion = mock(PlaylistExpansionService.class);
        when(expansion.expandAndMerge(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        var seeding = new CatalogSeedingService(mock(PendingImportRepository.class), lookup, mock(PendingImportProcessor.class),
                new MetadataPriorityCoordinator(), expansion, mock(SongRepository.class), new SyncTaskExecutor(), LEGACY_DRAIN_QUOTA,
                SETTINGS, engine);
        admin = new AdminPipelineService(repository, engine, SETTINGS, usage, seeding);
        songs = new AdminSongPipelineService(core, jobRepository, engine, admin, JsonMapper.builder().build());
    }

    private <Result> Result transaction(Supplier<Result> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    private PipelineRun start(String video) { return engine.startRun(video, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, null, null); }

    private PipelineJob lease() { return engine.leaseNext(PipelineStage.YOUTUBE_METADATA, OWNER, PipelineJobService.DEFAULT_LEASE_DURATION).orElseThrow(); }

    private long song(String video) {
        return core.queryForObject("INSERT INTO songs (youtube_id, title, release_year, verification_status) VALUES (?, 'Control song', ?, 'NEEDS_REVIEW') RETURNING id",
                Long.class, video, YEAR);
    }

    @Test
    void stageAndGlobalPauseResumeControlsChangeRealRows() {
        start(VIDEO);
        transaction(() -> admin.pause("youtube-metadata", true));
        assertThat(engine.leaseNext(PipelineStage.YOUTUBE_METADATA, OWNER, PipelineJobService.DEFAULT_LEASE_DURATION)).isEmpty();
        assertThat(admin.queues().stages()).anySatisfy(stage -> {
            assertThat(stage.stage()).isEqualTo(PipelineStage.YOUTUBE_METADATA);
            assertThat(stage.state()).isEqualTo(org.dariusturcu.backend.model.admin.QueueState.PAUSED);
            assertThat(stage.pausedReason()).isNotBlank();
            assertThat(stage.estimatedDrainSeconds()).isNull();
        });
        transaction(() -> admin.pause("youtube-metadata", false));
        assertThat(lease()).isNotNull();
        transaction(() -> admin.pauseAll(true));
        assertThat(core.queryForObject("SELECT bool_and(paused) FROM stage_controls", Boolean.class)).isTrue();
        transaction(() -> admin.pauseAll(false));
        assertThat(core.queryForObject("SELECT bool_or(paused) FROM stage_controls", Boolean.class)).isFalse();
    }

    @Test
    void runRetryDiscardBumpAndRetryAllOperateOnRealJobs() {
        PipelineRun first = start(VIDEO);
        PipelineRun second = start(OTHER_VIDEO);
        transaction(() -> admin.bump(second.id()));
        PipelineJob bumped = lease();
        assertThat(bumped.runId()).isEqualTo(second.id());
        engine.failPermanent(bumped.id(), OWNER, "Unavailable");
        transaction(() -> admin.retry(second.id()));
        assertThat(engine.findRun(second.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.ACTIVE);
        engine.failPermanent(lease().id(), OWNER, "Unavailable");
        assertThat(transaction(admin::retryAll).affected()).isEqualTo(1);
        transaction(() -> admin.discard(first.id()));
        assertThat(engine.findRun(first.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.CANCELLED);
    }

    @Test
    void batchesRecordDuplicateSkipsAndCancelTheirRuns() {
        song(VIDEO);
        var first = transaction(() -> admin.enqueue(new AdminCatalogSeedingRequest(null, List.of(VIDEO, OTHER_VIDEO))));
        assertThat(first.batchId()).isNotNull();
        assertThat(first.skippedAlreadyKnownYoutubeIds()).containsExactly(VIDEO);
        var duplicate = transaction(() -> admin.enqueue(new AdminCatalogSeedingRequest(null, List.of(OTHER_VIDEO))));
        assertThat(duplicate.skippedAlreadyQueuedYoutubeIds()).containsExactly(OTHER_VIDEO);
        assertThat(admin.batches(null).items()).allSatisfy(batch -> assertThat(batch.skippedDuplicates()).isEqualTo(1));
        transaction(() -> admin.cancel(first.batchId()));
        assertThat(admin.batches(null).items().stream().filter(batch -> batch.id() == first.batchId()).findFirst().orElseThrow().cancelled()).isEqualTo(1);
        assertThatThrownBy(() -> core.update("UPDATE import_batches SET skipped_duplicates = -1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void stageAndRunPagesHaveRealCountsAndFixedPageSize() {
        int count = AdminPipelineService.PAGE_SIZE + EXTRA_PAGE_ITEMS;
        IntStream.range(0, count).forEach(number -> start("paged" + number));
        var first = admin.jobs("youtube-metadata", null, null);
        var second = admin.jobs("youtube-metadata", null, 1);
        assertThat(first.items()).hasSize(AdminPipelineService.PAGE_SIZE);
        assertThat(second.items()).hasSize(EXTRA_PAGE_ITEMS);
        assertThat(first.totalElements()).isEqualTo(count);
        assertThat(admin.runs(PipelineRunStatus.ACTIVE, PipelineLane.BACKFILL, null).items()).hasSize(AdminPipelineService.PAGE_SIZE);
        engine.failTransient(lease().id(), OWNER, "Timeout", false);
        assertThat(admin.jobs("youtube-metadata", "RETRYING", null).items()).singleElement()
                .satisfies(job -> assertThat(job.lastError()).isEqualTo("Timeout"));
    }

    private org.dariusturcu.backend.model.admin.QueueStageDTO stageOf(PipelineStage stage) {
        return admin.queues().stages().stream().filter(queue -> queue.stage() == stage).findFirst().orElseThrow();
    }

    @Test
    void queuesReportCooldownPaceEstimatesAndTheEmbeddingDepth() {
        start(VIDEO);
        var overviewWithoutTimings = admin.queues();
        assertThat(overviewWithoutTimings.estimatedDrainSeconds()).isNull();
        assertThat(stageOf(PipelineStage.YOUTUBE_METADATA).estimatedDrainSeconds()).isNull();
        assertThat(stageOf(PipelineStage.YOUTUBE_METADATA).averageResponseMs()).isNull();

        PipelineRun finished = start(OTHER_VIDEO);
        core.update("""
                UPDATE pipeline_jobs SET status = 'DONE', started_at = now() - interval '20 seconds',
                    next_attempt_at = now() - interval '25 seconds', finished_at = now() WHERE run_id = ?
                """, finished.id());
        core.update("UPDATE stage_pace SET cooldown_until = now() + interval '1 hour' WHERE stage = 'YOUTUBE_METADATA'");
        var overview = admin.queues();
        assertThat(overview.youtubeReserveUnits()).isEqualTo(SETTINGS.youtubeReserveUnits());
        var youtube = overview.stages().stream().filter(stage -> stage.stage() == PipelineStage.YOUTUBE_METADATA)
                .findFirst().orElseThrow();
        assertThat(youtube.state()).isEqualTo(org.dariusturcu.backend.model.admin.QueueState.COOLING_DOWN);
        assertThat(youtube.currentDelayMs()).isEqualTo(SOURCE_DELAY_MS);
        assertThat(youtube.sourceRequestMs()).isEqualTo(SOURCE_RESPONSE_MS);
        assertThat(youtube.averageResponseMs()).isEqualTo(JOB_DURATION_MS);
        assertThat(youtube.averageQueueWaitMs()).isEqualTo(QUEUE_WAIT_MS);
        assertThat(youtube.finishedLastHour()).isEqualTo(1);
        assertThat(youtube.jobsPerMinute()).isEqualTo(DrainEstimate.measuredJobsPerMinute(1));
        assertThat(youtube.concurrencyLimit()).isEqualTo(StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT);
        assertThat(youtube.waitingDue()).isEqualTo(1);
        assertThat(youtube.waitingScheduled()).isZero();
        assertThat(youtube.estimatedDrainSeconds()).isGreaterThanOrEqualTo(COOLDOWN_SECONDS);
        assertThat(overview.estimatedDrainSeconds()).isEqualTo(youtube.estimatedDrainSeconds());
        long song = song("adminvideo3");
        core.update("UPDATE songs SET verification_status = 'VERIFIED' WHERE id = ?", song);
        assertThat(admin.jobs("embedding", null, null).items()).singleElement()
                .satisfies(job -> assertThat(job.id()).isEqualTo(song));
    }

    @Test
    void theEmbeddingRowReportsClaimsRetriesAndStoredEmbeddingsFromTheV41Queue() {
        long claimed = verifiedSong("embedvideo1");
        long retried = verifiedSong("embedvideo2");
        long stored = verifiedSong("embedvideo3");
        verifiedSong("embedvideo4");
        core.update("UPDATE song_embedding_queue SET attempts = attempts + 1, available_at = now() + interval '5 minutes' WHERE song_id IN (?, ?, ?)",
                claimed, retried, stored);
        core.update("UPDATE song_embedding_queue SET available_at = now() + interval '2 minutes' WHERE song_id = ?", retried);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            core.update("DELETE FROM song_embedding_queue WHERE song_id = ?", stored);
            core.update("UPDATE songs SET embedding = array_fill(?::real, ARRAY[?])::vector WHERE id = ?",
                    EMBEDDING_COMPONENT, EMBEDDING_DIMENSIONS, stored);
        });

        var embedding = stageOf(PipelineStage.EMBEDDING);
        assertThat(embedding.inProgress()).isEqualTo(1);
        assertThat(embedding.waiting()).isEqualTo(2);
        assertThat(embedding.waitingDue()).isEqualTo(1);
        assertThat(embedding.retrying()).isEqualTo(1);
        assertThat(embedding.failed()).isZero();
        assertThat(embedding.doneToday()).isEqualTo(1);
        assertThat(embedding.finishedLastHour()).isEqualTo(1);
        assertThat(embedding.averageResponseMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(core.queryForObject("SELECT embedding_started_at IS NOT NULL FROM songs WHERE id = ?", Boolean.class, stored)).isTrue();
        assertThat(admin.jobs("embedding", "IN_PROGRESS", null).items()).singleElement().satisfies(job -> {
            assertThat(job.id()).isEqualTo(claimed);
            assertThat(job.failedTries()).isZero();
        });
        assertThat(admin.jobs("embedding", "RETRYING", null).items()).singleElement().satisfies(job -> {
            assertThat(job.id()).isEqualTo(retried);
            assertThat(job.failedTries()).isEqualTo(1);
        });
        core.update("UPDATE songs SET title = 'Renamed' WHERE id = ?", stored);
        assertThat(core.queryForObject("SELECT embedding_stored_at IS NULL FROM songs WHERE id = ?", Boolean.class, stored)).isTrue();
    }

    private long verifiedSong(String video) {
        long songId = song(video);
        core.update("UPDATE songs SET verification_status = 'VERIFIED' WHERE id = ?", songId);
        return songId;
    }

    @Test
    void finishedJobsHideStaleFailureDataInEveryReadPath() {
        PipelineRun run = start(VIDEO);
        long songId = song(VIDEO);
        core.update("""
                UPDATE pipeline_jobs SET status = 'DONE', attempts = 1, failure_kind = 'TRANSIENT',
                    failure_stage = 'YOUTUBE_METADATA', failure_reason = ?, started_at = now(), finished_at = now()
                WHERE run_id = ?
                """, STALE_REASON, run.id());
        core.update("UPDATE pipeline_runs SET status = 'DONE', finished_at = now() WHERE id = ?", run.id());

        assertThat(admin.jobs("youtube-metadata", null, null).items()).singleElement().satisfies(job -> {
            assertThat(job.lastError()).isNull();
            assertThat(job.failingStage()).isNull();
            assertThat(job.failedTries()).isEqualTo(1);
            assertThat(job.maxTries()).isEqualTo(PipelineJobService.DEFAULT_MAX_ATTEMPTS);
        });
        assertThat(admin.run(engine.findRun(run.id()).orElseThrow()).jobs())
                .filteredOn(job -> job.stage() == PipelineStage.YOUTUBE_METADATA)
                .singleElement().satisfies(job -> assertThat(job.lastError()).isNull());
        assertThat(songs.findings(songId).sources()).singleElement()
                .satisfies(finding -> assertThat(finding.failureReason()).isNull());
        var catalog = new org.dariusturcu.backend.repository.AdminSongCatalogRepository(core, SETTINGS);
        assertThat(catalog.pipelineHistory(VIDEO)).singleElement()
                .satisfies(history -> assertThat(history.getFailureReason()).isNull());

        PipelineRun retrying = start(OTHER_VIDEO);
        engine.failTransient(lease().id(), OWNER, STALE_REASON, false);
        assertThat(admin.jobs("youtube-metadata", "RETRYING", null).items()).singleElement().satisfies(job -> {
            assertThat(job.runId()).isEqualTo(retrying.id());
            assertThat(job.lastError()).isEqualTo(STALE_REASON);
            assertThat(job.failedTries()).isEqualTo(1);
        });
    }

    @Test
    void jobFiltersMatchTheStageCounts() {
        PipelineRun discarded = start(VIDEO);
        start(OTHER_VIDEO);
        PipelineJob failing = engine.leaseNext(PipelineStage.YOUTUBE_METADATA, OWNER, PipelineJobService.DEFAULT_LEASE_DURATION)
                .orElseThrow();
        engine.failPermanent(failing.id(), OWNER, "Unavailable");
        transaction(() -> admin.discard(failing.runId()));
        var youtube = stageOf(PipelineStage.YOUTUBE_METADATA);
        assertThat(admin.jobs("youtube-metadata", "FAILED", null).totalElements()).isEqualTo(youtube.failed()).isZero();
        assertThat(admin.jobs("youtube-metadata", "WAITING", null).totalElements()).isEqualTo(youtube.waiting()).isEqualTo(1);
        assertThat(discarded.id()).isPositive();
    }

    @Test
    void expiredLeaseFiltersMatchTheQueueCounts() {
        start(VIDEO);
        lease();
        core.update("UPDATE pipeline_jobs SET lease_expires_at = now() - interval '1 second'");

        var youtube = stageOf(PipelineStage.YOUTUBE_METADATA);
        assertThat(admin.jobs("youtube-metadata", "WAITING", null).totalElements()).isEqualTo(youtube.waiting());
        assertThat(admin.jobs("youtube-metadata", "RETRYING", null).totalElements()).isEqualTo(youtube.retrying());
        assertThat(admin.jobs("youtube-metadata", "IN_PROGRESS", null).totalElements()).isEqualTo(youtube.inProgress()).isZero();
    }

    @Test
    void runEmbeddingProgressDistinguishesTheCurrentClaimFromFailedTries() {
        PipelineRun run = start(VIDEO);
        long songId = verifiedSong(VIDEO);
        core.update("UPDATE pipeline_runs SET status = 'DONE', finished_at = now() WHERE id = ?", run.id());
        core.update("UPDATE song_embedding_queue SET attempts = attempts + 1, available_at = now() + interval '5 minutes' WHERE song_id = ?", songId);

        assertThat(admin.run(engine.findRun(run.id()).orElseThrow()).jobs())
                .filteredOn(job -> job.stage() == PipelineStage.EMBEDDING).singleElement().satisfies(job -> {
                    assertThat(job.status()).isEqualTo(PipelineJobStatus.IN_PROGRESS);
                    assertThat(job.retrying()).isFalse();
                    assertThat(job.failedTries()).isZero();
                    assertThat(job.maxTries()).isNull();
                });
    }

    @Test
    void runNowAndSongRerunBypassKnownSongFilteringAndBumpPriority() {
        long song = song(VIDEO);
        var first = transaction(() -> admin.runNow(VIDEO));
        var rerun = transaction(() -> songs.rerun(song));
        assertThat(rerun.id()).isEqualTo(first.id());
        assertThat(engine.jobsOfRun(first.id())).singleElement().satisfies(job -> assertThat(job.priority()).isPositive());
    }

    @Test
    void songUpdatesApplyYearAndStatusAndTriggerEmbeddingWork() {
        long song = song(VIDEO);
        transaction(() -> songs.update(song, new UpdateCatalogSongRequest(UPDATED_YEAR, VerificationStatus.VERIFIED)));
        assertThat(core.queryForObject("SELECT release_year FROM songs WHERE id = ?", Integer.class, song)).isEqualTo(UPDATED_YEAR);
        assertThat(core.queryForObject("SELECT verification_status FROM songs WHERE id = ?", String.class, song)).isEqualTo("VERIFIED");
        assertThat(core.queryForObject("SELECT song_id FROM song_embedding_queue", Long.class)).isEqualTo(song);
        assertThatThrownBy(() -> transaction(() -> songs.update(song, new UpdateCatalogSongRequest(AdminSongPipelineService.MINIMUM_YEAR - 1, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void songFindingsComeFromTheLatestRunAndAreAbsentWithoutARun() {
        long song = song(VIDEO);
        assertThat(songs.findings(song).sources()).isNull();
        start(VIDEO);
        engine.complete(lease().id(), OWNER, "{\"outcome\":\"IDENTIFIED\",\"title\":\"Control song\"}");
        assertThat(songs.findings(song).sources()).hasSize(4);
        assertThat(songs.findings(song).sources()).anySatisfy(source -> {
            assertThat(source.stage()).isEqualTo(PipelineStage.YOUTUBE_METADATA);
            assertThat(source.result().get("title")).isEqualTo("Control song");
        });
        for (PipelineStage stage : StageDependencies.SOURCE_STAGES) {
            PipelineJob source = engine.leaseNext(stage, OWNER, PipelineJobService.DEFAULT_LEASE_DURATION).orElseThrow();
            engine.complete(source.id(), OWNER, "{\"earliestYear\":1999}");
        }
        assertThat(songs.findings(song).reviewReason()).contains("same earliest year");
    }

    @Test
    void deletingACatalogSongRemovesItsLinksAndCancelsAnActiveRun() {
        long song = song(VIDEO);
        long user = core.queryForObject("INSERT INTO users (username) VALUES ('owner') RETURNING id", Long.class);
        long playlist = core.queryForObject("INSERT INTO playlists (name, invite_code, owner_id) VALUES ('List', 'invite', ?) RETURNING id", Long.class, user);
        core.update("INSERT INTO song_playlists (song_id, playlist_id) VALUES (?, ?)", song, playlist);
        core.update("INSERT INTO alternate_youtube_ids (song_id, youtube_id) VALUES (?, ?)", song, OTHER_VIDEO);
        core.update("INSERT INTO song_artists (song_id, name, role, display_order) VALUES (?, 'Artist', 'MAIN', 0)", song);
        PipelineRun failedRun = start(VIDEO);
        engine.failPermanent(lease().id(), OWNER, "Old failure");
        PipelineRun run = start(VIDEO);
        transaction(() -> songs.delete(song));
        assertThat(core.queryForObject("SELECT count(*) FROM songs", Long.class)).isZero();
        assertThat(core.queryForObject("SELECT count(*) FROM alternate_youtube_ids", Long.class)).isZero();
        assertThat(core.queryForObject("SELECT count(*) FROM song_playlists", Long.class)).isZero();
        assertThat(core.queryForObject("SELECT count(*) FROM playlists", Long.class)).isEqualTo(1);
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.CANCELLED);
        assertThat(engine.findRun(failedRun.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.CANCELLED);
    }

    @Test
    void aSongQueuedForAnActiveGameCannotBeDeleted() {
        long song = song(VIDEO);
        long group = core.queryForObject("""
                INSERT INTO groups (invite_code, join_code, status, dj_mode, win_condition_card_count, created_at, expires_at)
                VALUES ('invite', 'ABCD', 'OPEN', 'FIXED', 5, now(), now() + interval '30 minutes') RETURNING id
                """, Long.class);
        long session = core.queryForObject("""
                INSERT INTO game_sessions (group_id, status, dj_mode, win_condition_card_count, current_round_number, created_at)
                VALUES (?, 'ACTIVE', 'FIXED', 5, 1, now()) RETURNING id
                """, Long.class, group);
        core.update("INSERT INTO game_session_song_queue (session_id, queue_position, song_id) VALUES (?, 0, ?)", session, song);
        assertThatThrownBy(() -> transaction(() -> songs.delete(song))).isInstanceOf(ConflictException.class);
        assertThat(core.queryForObject("SELECT count(*) FROM songs WHERE id = ?", Long.class, song)).isEqualTo(1);
    }

    @Test
    void embeddingPauseStopsTheV41SchedulerUntilResume() {
        long song = song(VIDEO);
        core.update("UPDATE songs SET verification_status = 'VERIFIED' WHERE id = ?", song);
        var builder = org.springframework.web.client.RestClient.builder().baseUrl("http://ai.test");
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var scheduler = new org.dariusturcu.backend.scheduling.SongEmbeddingScheduler(core, builder.build(), SETTINGS);
        transaction(() -> admin.pause("embedding", true));
        scheduler.indexNext();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai.test/catalog/index-next"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());
        transaction(() -> admin.pause("embedding", false));
        scheduler.indexNext();
        server.verify();
    }

    @Test
    void retryAllDoesNotRestartFailedWorkFromACancelledBatch() {
        var batch = transaction(() -> admin.enqueue(new AdminCatalogSeedingRequest(null, List.of(VIDEO))));
        PipelineJob job = lease();
        engine.failPermanent(job.id(), OWNER, "Unavailable");
        transaction(() -> admin.cancel(batch.batchId()));
        assertThat(transaction(admin::retryAll).affected()).isZero();
        assertThatThrownBy(() -> transaction(() -> admin.retry(job.runId()))).isInstanceOf(ConflictException.class);
    }
    @Test
    void runStateCountsRemainGlobalWhenThePageIsFiltered() {
        PipelineRun waiting = start(VIDEO);
        PipelineRun retrying = start(OTHER_VIDEO);
        transaction(() -> admin.bump(retrying.id()));
        engine.failTransient(lease().id(), OWNER, "Timeout", false);
        var page = admin.runs(null, null, RunQueueState.RETRYING, null);
        assertThat(page.items()).singleElement().satisfies(run -> assertThat(run.id()).isEqualTo(retrying.id()));
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.counts()).containsEntry(RunQueueState.WAITING, 1L).containsEntry(RunQueueState.RETRYING, 1L);
        assertThat(admin.runs(null, null, RunQueueState.WAITING, null).items()).singleElement()
                .satisfies(run -> assertThat(run.id()).isEqualTo(waiting.id()));
        transaction(() -> admin.retry(retrying.id()));
        assertThat(engine.jobsOfRun(retrying.id())).singleElement().satisfies(job -> {
            assertThat(job.attempts()).isEqualTo(1);
            assertThat(job.nextAttemptAt()).isBeforeOrEqualTo(Instant.now());
        });
    }

    @Test
    void runRowsIncludeBatchArtistsAndReadOnlyEmbeddingProgress() {
        var batch = transaction(() -> admin.enqueue(new AdminCatalogSeedingRequest(null, List.of(VIDEO))));
        long song = song(VIDEO);
        core.update("INSERT INTO song_artists (song_id, name, role, display_order) VALUES (?, 'Artist', 'MAIN', 0)", song);
        var run = admin.runs(null, null, null).items().getFirst();
        assertThat(run.batchId()).isEqualTo(batch.batchId());
        assertThat(run.batchLabel()).isNotBlank();
        assertThat(run.artists()).containsExactly("Artist");
        core.update("UPDATE pipeline_runs SET status = 'DONE', finished_at = now() WHERE id = ?", run.id());
        assertThat(admin.runs(null, null, null).items().getFirst().jobs()).anySatisfy(job -> {
            assertThat(job.stage()).isEqualTo(PipelineStage.EMBEDDING);
            assertThat(job.status()).isEqualTo(PipelineJobStatus.SKIPPED);
        });
        assertThat(core.queryForObject("SELECT count(*) FROM pipeline_jobs WHERE stage = 'EMBEDDING'", Long.class)).isZero();
    }

    @Test
    void stageScopedRetryLeavesOtherStagesFailed() {
        PipelineRun run = start(VIDEO);
        engine.failPermanent(lease().id(), OWNER, "Unavailable");
        assertThat(transaction(() -> admin.retryAll("musicbrainz")).affected()).isZero();
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(transaction(() -> admin.retryAll("youtube-metadata")).affected()).isEqualTo(1);
    }

    @Test
    void doneTodayFilterExcludesPreviousDaysAndWaitingRowsFollowLeasePriority() {
        start(VIDEO);
        PipelineRun priority = start(OTHER_VIDEO);
        transaction(() -> admin.bump(priority.id()));
        assertThat(admin.jobs("youtube-metadata", "WAITING", null).items().getFirst().runId()).isEqualTo(priority.id());
        engine.complete(lease().id(), OWNER, "{\"outcome\":\"IDENTIFIED\",\"title\":\"Song\"}");
        assertThat(admin.jobs("youtube-metadata", "DONE_TODAY", null).totalElements()).isEqualTo(1);
        core.update("UPDATE pipeline_jobs SET finished_at = now() - interval '2 days' WHERE status = 'DONE'");
        assertThat(admin.jobs("youtube-metadata", "DONE_TODAY", null).totalElements()).isZero();
    }

}
