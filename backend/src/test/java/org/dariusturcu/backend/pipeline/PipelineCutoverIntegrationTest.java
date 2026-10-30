package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.service.ApiUsageService;
import org.dariusturcu.backend.service.SongCatalogService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(classes = PipelineCutoverIntegrationTest.TestConfig.class)
class PipelineCutoverIntegrationTest {
    private static final String VIDEO_ID = "cutover0001";
    private static final String OTHER_VIDEO = "cutover0002";
    private static final String THIRD_VIDEO = "cutover0003";
    private static final String OWNER = "test-stage-worker";
    private static final int YEAR = 1999;
    private static final int DIFFERENT_YEAR = 2001;
    private static final long AVAILABLE_UNITS = 10_000;
    private static final long BELOW_RESERVE = PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS - 1;
    private static final Duration PACE_WAIT = Duration.ofSeconds(45);
    private static final int CONCURRENT_CALLERS = 4;
    private static final long CONCURRENT_TIMEOUT_SECONDS = 30;
    private static final int SINGLE_SLOT = 1;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    @Import({PipelineSettings.class, PipelineJobRepository.class, PipelineJobService.class, StagePayloads.class,
            PipelineCompletionService.class, PipelineStageWorker.class, SongCatalogService.class,
            StageConcurrency.class, StageWakeUps.class})
    static class TestConfig {
        @Bean
        JdbcTemplate coreJdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

        @Bean
        ApiUsageService usage() { return mock(ApiUsageService.class); }

        @Bean
        FakeStages stages(ObjectMapper mapper) { return new FakeStages(mapper); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.flyway.enabled", () -> false);
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();
    }

    @Autowired PipelineJobService engine;
    @Autowired PipelineStageWorker worker;
    @Autowired JdbcTemplate core;
    @Autowired ApiUsageService usage;
    @Autowired FakeStages stages;
    @Autowired SongRepository songs;

    @BeforeEach
    void reset() {
        core.execute("ALTER TABLE songs DROP CONSTRAINT IF EXISTS cutover_test_save_failure");
        core.execute("TRUNCATE songs, pipeline_runs, import_batches, pending_imports RESTART IDENTITY CASCADE");
        core.execute("TRUNCATE game_session_song_queue, game_sessions, groups RESTART IDENTITY CASCADE");
        core.update("UPDATE stage_controls SET paused = false, paused_reason = NULL, paused_at = NULL");
        stages.calls.clear();
        stages.failure = null;
        stages.discogsYear = YEAR;
        stages.youtubeResult = null;
        when(usage.youtubeUnitsRemaining()).thenReturn(AVAILABLE_UNITS);
    }

    PipelineRun start(String video) {
        return engine.startRun(video, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, null, null);
    }

    void sources() {
        worker.work(PipelineStage.MUSICBRAINZ);
        worker.work(PipelineStage.DISCOGS);
        worker.work(PipelineStage.WIKIDATA);
    }

    PipelineJob youtubeJob(long runId) {
        return engine.jobsOfRun(runId).stream().filter(job -> job.stage() == PipelineStage.YOUTUBE_METADATA)
                .findFirst().orElseThrow();
    }

    @Test
    void agreeingSourcesSaveExactlyOnceAndOnlyV41QueuesTheEmbedding() {
        PipelineRun run = start(VIDEO_ID);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        sources();
        worker.work(PipelineStage.LLM_RECONCILIATION);

        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(songs.findByYoutubeId(VIDEO_ID)).hasSize(1);
        assertThat(songs.findByYoutubeId(VIDEO_ID).getFirst().getReleaseYear()).isEqualTo(YEAR);
        assertThat(stages.calls).doesNotContain(PipelineStage.WIKIPEDIA, PipelineStage.LLM_RECONCILIATION);
        assertThat(engine.jobsOfRun(run.id())).noneMatch(job -> job.stage() == PipelineStage.EMBEDDING);
        assertThat(core.queryForObject("SELECT count(*) FROM song_embedding_queue", Long.class)).isEqualTo(1);
        assertThat(engine.stageCounts()).anySatisfy(counts -> {
            assertThat(counts.stage()).isEqualTo(PipelineStage.EMBEDDING);
            assertThat(counts.waiting()).isEqualTo(1);
        });
    }

    @Test
    void disagreeingSourcesRunWikipediaAndReconciliationBeforeSaving() {
        stages.discogsYear = DIFFERENT_YEAR;
        PipelineRun run = start(VIDEO_ID);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        assertThat(songs.findByYoutubeId(VIDEO_ID)).isEmpty();
        worker.work(PipelineStage.WIKIPEDIA);
        worker.work(PipelineStage.LLM_RECONCILIATION);

        assertThat(stages.calls).contains(PipelineStage.WIKIPEDIA, PipelineStage.LLM_RECONCILIATION);
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(songs.findByYoutubeId(VIDEO_ID)).hasSize(1);
    }

    @Test
    void transientProviderFailureRetriesWithoutCreatingOrFailingASong() {
        PipelineRun run = start(VIDEO_ID);
        stages.failure = new AiStageFailure("Provider timeout", FailureClassifier.TRANSIENT, null);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(youtubeJob(run.id()).attempts()).isEqualTo(1);
        assertThat(youtubeJob(run.id()).status()).isEqualTo(PipelineJobStatus.WAITING);
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(songs.findByYoutubeId(VIDEO_ID)).isEmpty();
        stages.failure = null;
        core.update("UPDATE pipeline_jobs SET next_attempt_at = now() - interval '1 minute'");
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
    }

    @Test
    void rejectedKeyPausesTheStageWithoutAnAttemptAndPermanentFailureFailsOnlyTheRun() {
        PipelineRun run = start(VIDEO_ID);
        stages.failure = new AiStageFailure("Provider rejected key", FailureClassifier.KEY_REJECTED, null);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(youtubeJob(run.id()).attempts()).isZero();
        assertThat(core.queryForObject("SELECT paused FROM stage_controls WHERE stage = 'YOUTUBE_METADATA'", Boolean.class)).isTrue();
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.ACTIVE);
        engine.setStagePaused(PipelineStage.YOUTUBE_METADATA, false, null);
        stages.failure = new AiStageFailure("Video unavailable", FailureClassifier.PERMANENT, null);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(songs.findByYoutubeId(VIDEO_ID)).isEmpty();
    }

    @Test
    void paceDeferralDoesNotUseAnAttemptOrLeaseTheJobEarly() {
        PipelineRun run = start(VIDEO_ID);
        stages.failure = new AiStageFailure("Deferred", FailureClassifier.TRANSIENT, PACE_WAIT);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        PipelineJob deferred = youtubeJob(run.id());
        assertThat(deferred.attempts()).isZero();
        assertThat(deferred.leaseOwner()).isNull();
        assertThat(engine.hasDueWork(PipelineStage.YOUTUBE_METADATA)).isFalse();
        assertThat(deferred.nextAttemptAt()).isAfter(deferred.updatedAt());
    }

    @Test
    void expiredLeasesAreRecoveredAndTheStageLimitHoldsAcrossWorkers() {
        PipelineRun run = start(VIDEO_ID);
        start(OTHER_VIDEO);
        PipelineJob leased = engine.leaseWithinLimit(PipelineStage.YOUTUBE_METADATA, OWNER, true, SINGLE_SLOT).orElseThrow();
        assertThat(engine.leaseWithinLimit(PipelineStage.YOUTUBE_METADATA, "other", true, SINGLE_SLOT)).isEmpty();
        core.update("UPDATE pipeline_jobs SET lease_expires_at = now() - interval '1 second' WHERE id = ?", leased.id());
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(youtubeJob(run.id()).attempts()).isEqualTo(1);
        assertThat(youtubeJob(run.id()).status()).isEqualTo(PipelineJobStatus.WAITING);
        assertThat(stages.calls).containsExactly(PipelineStage.YOUTUBE_METADATA);
    }

    @Test
    void oneTickDrainsEveryDueJobOfTheStage() {
        List<PipelineRun> runs = List.of(start(VIDEO_ID), start(OTHER_VIDEO), start(THIRD_VIDEO));
        assertThat(worker.work(PipelineStage.YOUTUBE_METADATA)).isEqualTo(runs.size());
        assertThat(runs).allSatisfy(run ->
                assertThat(youtubeJob(run.id()).status()).isEqualTo(PipelineJobStatus.DONE));
    }

    @Test
    void userLaneIgnoresReserveAndAnExistingBackfillRunIsPromoted() {
        start(VIDEO_ID);
        PipelineRun backlog = start(OTHER_VIDEO);
        when(usage.youtubeUnitsRemaining()).thenReturn(BELOW_RESERVE);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(stages.calls).isEmpty();
        PipelineRun userRun = engine.startRun(OTHER_VIDEO, PipelineLane.USER_IMPORT,
                PendingImportOrigin.FAST_TIER_RECHECK, null, YEAR);
        assertThat(userRun.id()).isEqualTo(backlog.id());
        assertThat(userRun.lane()).isEqualTo(PipelineLane.USER_IMPORT);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(youtubeJob(userRun.id()).status()).isEqualTo(PipelineJobStatus.DONE);
        assertThat(core.queryForObject("SELECT status FROM pipeline_jobs WHERE run_id <> ?", String.class, userRun.id()))
                .isEqualTo(PipelineJobStatus.WAITING.name());
    }

    @Test
    void saveFailureRollsBackStageCompletionAndLeavesTheRunRetrying() {
        PipelineRun run = start(VIDEO_ID);
        core.execute("ALTER TABLE songs ADD CONSTRAINT cutover_test_save_failure CHECK (youtube_id <> 'cutover0001')");
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        assertThat(songs.findByYoutubeId(VIDEO_ID)).isEmpty();
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.ACTIVE);
        PipelineJob failedSave = engine.jobsOfRun(run.id()).stream().filter(job -> job.stage() == PipelineStage.WIKIDATA)
                .findFirst().orElseThrow();
        assertThat(failedSave.status()).isEqualTo(PipelineJobStatus.WAITING);
        assertThat(failedSave.attempts()).isEqualTo(1);
        assertThat(engine.findRun(run.id()).orElseThrow().readyToSaveAt()).isNull();
        core.execute("ALTER TABLE songs DROP CONSTRAINT cutover_test_save_failure");
        core.update("UPDATE pipeline_jobs SET next_attempt_at = now() - interval '1 minute'");
        worker.work(PipelineStage.WIKIDATA);
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
    }

    @Test
    void concurrentDuplicateEnqueuesShareOneRunAndOneSavedSong() throws Exception {
        try (var executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS)) {
            List<java.util.concurrent.Future<PipelineRun>> futures = new ArrayList<>();
            for (int caller = 0; caller < CONCURRENT_CALLERS; caller++) {
                futures.add(executor.submit(() -> start(VIDEO_ID)));
            }
            List<Long> runIds = new ArrayList<>();
            for (var future : futures) {
                runIds.add(future.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS).id());
            }
            assertThat(runIds).containsOnly(runIds.getFirst());
        }
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        assertThat(songs.findByYoutubeId(VIDEO_ID)).hasSize(1);
    }

    @Test
    void catalogAndDashboardReadRunJobsOnlyOnTheNewPath() {
        var repository = new org.dariusturcu.backend.repository.AdminSongCatalogRepository(core);
        PipelineRun run = start(VIDEO_ID);
        assertThat(repository.findPage(null, null, false, 25, 0)).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(org.dariusturcu.backend.model.song.CatalogSongStatus.IN_QUEUE);
            assertThat(row.latestQueueOrigin()).isEqualTo(PendingImportOrigin.ADMIN_SEED);
        });
        assertThat(repository.countQueue().waiting()).isEqualTo(1);
        stages.failure = new AiStageFailure("Gone", FailureClassifier.PERMANENT, null);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(repository.countByStatus(null).get(org.dariusturcu.backend.model.song.CatalogSongStatus.ERROR)).isEqualTo(1);
        assertThat(repository.pipelineHistory(VIDEO_ID)).singleElement().satisfies(history ->
                assertThat(history.getFailureReason()).isEqualTo("Gone"));
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.FAILED);
    }

    @Test
    void patientYearIsRecordedWhenTheRunAndSongCommitTogether() {
        int provisionalYear = DIFFERENT_YEAR;
        engine.startRun(VIDEO_ID, PipelineLane.USER_IMPORT, PendingImportOrigin.FAST_TIER_RECHECK, null, provisionalYear);
        core.update("""
                INSERT INTO pending_imports (youtube_id, status, origin, enqueued_at, provisional_year)
                VALUES (?, 'PENDING', 'FAST_TIER_RECHECK', now() AT TIME ZONE 'UTC', ?)
                """, VIDEO_ID, provisionalYear);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        assertThat(core.queryForObject("SELECT patient_year FROM pending_imports", Integer.class)).isEqualTo(YEAR);
        assertThat(core.queryForObject("SELECT status FROM pending_imports", String.class)).isEqualTo("DONE");
        var repository = new org.dariusturcu.backend.repository.AdminSongCatalogRepository(core);
        assertThat(repository.pipelineHistory(VIDEO_ID)).singleElement().satisfies(history ->
                assertThat(history.getPatientYear()).isEqualTo(YEAR));
    }

    @Test
    void aPrecheckRejectionStopsTheRunBeforeAnySourceJobsExist() {
        PipelineRun run = start(VIDEO_ID);
        stages.youtubeResult = stages.mapper.readTree("{\"outcome\":\"REJECTED\",\"rejectionReason\":\"NOT_MUSIC\"}");
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(engine.jobsOfRun(run.id())).hasSize(1);
        assertThat(songs.count()).isZero();
    }

    @Test
    void aVerifiedDuplicateFinishesFromYoutubeWithoutInsertingAnotherSong() {
        start(VIDEO_ID);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        long canonicalSongId = songs.findByYoutubeId(VIDEO_ID).getFirst().getId();
        stages.calls.clear();
        stages.youtubeResult = stages.mapper.readTree("""
                {"outcome":"DUPLICATE","result":{"status":"SUCCESS","content":{
                 "title":"Test song","main_artists":["Test artist"],"featured_artists":[],
                 "release_year":1999,"verification_status":"VERIFIED","canonical_song_id":%d}}}
                """.formatted(canonicalSongId));
        PipelineRun duplicate = start(OTHER_VIDEO);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        assertThat(engine.findRun(duplicate.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(stages.calls).containsExactly(PipelineStage.YOUTUBE_METADATA);
        assertThat(songs.count()).isEqualTo(1);
        assertThat(core.queryForObject("SELECT song_id FROM alternate_youtube_ids WHERE youtube_id = ?",
                Long.class, OTHER_VIDEO)).isEqualTo(canonicalSongId);
    }

    @Test
    void completionWithExistingNormalizedKeyAttachesAlternateIdAndCreatesNoSong() {
        start(VIDEO_ID);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        long existingSongId = songs.findByYoutubeId(VIDEO_ID).getFirst().getId();

        stages.calls.clear();
        stages.youtubeResult = null;
        PipelineRun duplicate = start(OTHER_VIDEO);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();

        assertThat(engine.findRun(duplicate.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(songs.count()).isEqualTo(1);
        assertThat(core.queryForObject("SELECT song_id FROM alternate_youtube_ids WHERE youtube_id = ?",
                Long.class, OTHER_VIDEO)).isEqualTo(existingSongId);
    }

    @Test
    void twoParallelCompletionsOfTheSameKeyCreateOneSong() throws Exception {
        PipelineRun firstRun = start(VIDEO_ID);
        PipelineRun secondRun = start(OTHER_VIDEO);

        worker.work(PipelineStage.YOUTUBE_METADATA);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        worker.work(PipelineStage.MUSICBRAINZ);
        worker.work(PipelineStage.MUSICBRAINZ);
        worker.work(PipelineStage.DISCOGS);
        worker.work(PipelineStage.DISCOGS);

        var pool = Executors.newFixedThreadPool(2);
        try {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var firstFuture = pool.submit(() -> {
                barrier.await(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                worker.work(PipelineStage.WIKIDATA);
                return null;
            });
            var secondFuture = pool.submit(() -> {
                barrier.await(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                worker.work(PipelineStage.WIKIDATA);
                return null;
            });

            firstFuture.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            secondFuture.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(songs.count()).isEqualTo(1);
        assertThat(core.queryForObject("SELECT count(*) FROM alternate_youtube_ids", Long.class)).isEqualTo(1);
        assertThat(engine.findRun(firstRun.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(engine.findRun(secondRun.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
    }

    private static final String STORED_TITLE = "Stored title";
    private static final int STORED_YEAR = 1980;

    private long insertStoredSong(String youtubeId, String verificationStatus) {
        core.execute("TRUNCATE game_session_song_queue, game_sessions, groups RESTART IDENTITY CASCADE");
        long songId = core.queryForObject("""
                INSERT INTO songs (title, release_year, youtube_id, verification_status, created_at)
                VALUES (?, ?, ?, ?, now()) RETURNING id
                """, Long.class, STORED_TITLE, STORED_YEAR, youtubeId, verificationStatus);
        core.update("INSERT INTO song_artists (song_id, name, role, display_order) VALUES (?, 'Stored artist', 'MAIN', 0)",
                songId);
        return songId;
    }

    private void putSongInActiveGameQueue(long songId) {
        core.update("""
                INSERT INTO groups (invite_code, join_code, status, dj_mode, win_condition_card_count, created_at)
                VALUES ('invite', 'ABCD', 'IN_GAME', 'ROTATING', 10, now())
                """);
        core.update("""
                INSERT INTO game_sessions (group_id, status, dj_mode, win_condition_card_count, current_round_number, created_at)
                SELECT id, 'ACTIVE', 'ROTATING', 10, 1, now() FROM groups
                """);
        core.update("INSERT INTO game_session_song_queue (session_id, queue_position, song_id) SELECT id, 0, ? FROM game_sessions",
                songId);
    }

    private PipelineRun runRecheckToCompletion(String youtubeId) {
        PipelineRun run = engine.startRun(youtubeId, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_RECHECK, null, null);
        worker.work(PipelineStage.YOUTUBE_METADATA);
        sources();
        return run;
    }

    @Test
    void aRecheckOutsideAGameRewritesAnUnverifiedSong() {
        long songId = insertStoredSong(VIDEO_ID, "NEEDS_REVIEW");

        PipelineRun run = runRecheckToCompletion(VIDEO_ID);

        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(core.queryForObject("SELECT release_year FROM songs WHERE id = ?", Integer.class, songId)).isEqualTo(YEAR);
        assertThat(core.queryForObject("SELECT title FROM songs WHERE id = ?", String.class, songId)).isEqualTo("Test song");
    }

    @Test
    void aRecheckThatFinishesWhileAGameHoldsTheSongLeavesItsYearTitleAndArtistsUntouched() {
        long songId = insertStoredSong(VIDEO_ID, "NEEDS_REVIEW");
        putSongInActiveGameQueue(songId);

        PipelineRun run = runRecheckToCompletion(VIDEO_ID);

        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(songs.count()).isEqualTo(1);
        assertThat(core.queryForObject("SELECT release_year FROM songs WHERE id = ?", Integer.class, songId)).isEqualTo(STORED_YEAR);
        assertThat(core.queryForObject("SELECT title FROM songs WHERE id = ?", String.class, songId)).isEqualTo(STORED_TITLE);
        assertThat(core.queryForObject("SELECT verification_status FROM songs WHERE id = ?", String.class, songId))
                .isEqualTo("NEEDS_REVIEW");
        assertThat(core.queryForList("SELECT name FROM song_artists WHERE song_id = ?", String.class, songId))
                .containsExactly("Stored artist");
        assertThat(core.queryForObject("SELECT count(*) FROM game_session_song_queue WHERE song_id = ?", Long.class, songId))
                .isEqualTo(1);
    }

    @Test
    void aVerifiedSongInAGameKeepsItsYearAndGetsNoProposalUntilTheGameEnds() {
        long songId = insertStoredSong(VIDEO_ID, "VERIFIED");
        putSongInActiveGameQueue(songId);

        runRecheckToCompletion(VIDEO_ID);

        assertThat(core.queryForObject("SELECT release_year FROM songs WHERE id = ?", Integer.class, songId)).isEqualTo(STORED_YEAR);
        assertThat(core.queryForObject("SELECT proposed_release_year FROM songs WHERE id = ?", Integer.class, songId)).isNull();
    }

    @Test
    void aRecheckOfAnAlternateVideoOfAGameSongCreatesNoSongAndChangesNothing() {
        long songId = insertStoredSong(VIDEO_ID, "NEEDS_REVIEW");
        core.update("INSERT INTO alternate_youtube_ids (youtube_id, song_id) VALUES (?, ?)", OTHER_VIDEO, songId);
        putSongInActiveGameQueue(songId);

        PipelineRun run = runRecheckToCompletion(OTHER_VIDEO);

        assertThat(engine.findRun(run.id()).orElseThrow().status()).isEqualTo(PipelineRunStatus.DONE);
        assertThat(songs.count()).isEqualTo(1);
        assertThat(core.queryForObject("SELECT release_year FROM songs WHERE id = ?", Integer.class, songId)).isEqualTo(STORED_YEAR);
    }

    static class FakeStages implements AiStageClient {
        final ObjectMapper mapper;
        final List<PipelineStage> calls = new ArrayList<>();
        AiStageFailure failure;
        JsonNode youtubeResult;
        int discogsYear = YEAR;

        FakeStages(ObjectMapper mapper) { this.mapper = mapper; }

        @Override
        public JsonNode execute(PipelineStage stage, JsonNode request) {
            calls.add(stage);
            if (failure != null) { throw failure; }
            if (stage == PipelineStage.YOUTUBE_METADATA && youtubeResult != null) { return youtubeResult; }
            return switch (stage) {
                case YOUTUBE_METADATA -> mapper.readTree("""
                        {"outcome":"IDENTIFIED","title":"Test song","artists":["Test artist"],
                         "featuredArtists":[],"color":"112233","video":{"durationSeconds":215}}
                        """);
                case MUSICBRAINZ, DISCOGS, WIKIDATA -> mapper.createObjectNode()
                        .put("earliestYear", stage == PipelineStage.DISCOGS ? discogsYear : YEAR)
                        .set("candidates", mapper.createArrayNode());
                case WIKIPEDIA -> mapper.createObjectNode().set("candidates", mapper.createArrayNode());
                case LLM_RECONCILIATION -> mapper.readTree("""
                        {"status":"SUCCESS","content":{"title":"Test song","main_artists":["Test artist"],
                         "featured_artists":[],"release_year":1999,"color":"112233","confidence":"medium",
                         "verification_status":"NEEDS_REVIEW","duration_seconds":215}}
                        """);
                default -> throw new IllegalArgumentException("Stage has no endpoint");
            };
        }
    }
}
