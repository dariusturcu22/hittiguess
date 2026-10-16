package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.service.ApiUsageService;
import org.dariusturcu.backend.service.SongCatalogService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the dispatcher on real threads against Postgres with an AI client that sleeps like a
 * slow provider, comparing the parallel, burst, and wake-up behavior with the earlier one job
 * per stage per poll.
 */
@Testcontainers
@SpringBootTest(classes = PipelineStageDispatcherIntegrationTest.TestConfig.class)
class PipelineStageDispatcherIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStageDispatcherIntegrationTest.class);
    private static final int SONG_COUNT = 6;
    private static final int YEAR = 1999;
    private static final long AVAILABLE_UNITS = 10_000;
    private static final Duration YOUTUBE_CALL = Duration.ofMillis(300);
    private static final Duration SOURCE_CALL = Duration.ofMillis(100);
    private static final Duration SHORT_POLL = Duration.ofSeconds(1);
    private static final Duration LONG_POLL = Duration.ofSeconds(30);
    private static final Duration FINISH_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration WAKE_UP_BOUND = Duration.ofSeconds(5);
    private static final Duration CHECK_PAUSE = Duration.ofMillis(20);
    private static final int SINGLE_SLOT = 1;
    private static final int SINGLE_JOB_BURST = 1;
    // The parallel run must take under a third of the earlier behavior's time.
    private static final long REQUIRED_SPEEDUP = 3;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    @Import({PipelineSettings.class, PipelineJobRepository.class, PipelineJobService.class, StagePayloads.class,
            PipelineCompletionService.class, SongCatalogService.class, StageWakeUps.class})
    static class TestConfig {
        @Bean
        JdbcTemplate coreJdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

        @Bean
        ApiUsageService usage() { return mock(ApiUsageService.class); }

        @Bean
        SlowStages stages(ObjectMapper mapper) { return new SlowStages(mapper); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.flyway.enabled", () -> false);
        properties.add("pipeline.stages.enabled", () -> true);
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();
    }

    @Autowired PipelineJobService engine;
    @Autowired StagePayloads payloads;
    @Autowired PipelineCompletionService completion;
    @Autowired PipelineSettings settings;
    @Autowired StageWakeUps engineWakeUps;
    @Autowired JdbcTemplate core;
    @Autowired ApiUsageService usage;
    @Autowired SlowStages stages;
    @Autowired SongRepository songs;

    private final List<PipelineStageDispatcher> dispatchers = new ArrayList<>();
    private final List<ThreadPoolTaskScheduler> schedulers = new ArrayList<>();
    private final List<ThreadPoolTaskExecutor> executors = new ArrayList<>();

    @BeforeEach
    void reset() {
        core.execute("TRUNCATE songs, pipeline_runs, import_batches, pending_imports RESTART IDENTITY CASCADE");
        core.update("UPDATE stage_controls SET paused = false, paused_reason = NULL, paused_at = NULL");
        stages.reset();
        when(usage.youtubeUnitsRemaining()).thenReturn(AVAILABLE_UNITS);
    }

    @AfterEach
    void stopDispatchers() {
        dispatchers.forEach(PipelineStageDispatcher::stop);
        schedulers.forEach(ThreadPoolTaskScheduler::shutdown);
        executors.forEach(ThreadPoolTaskExecutor::shutdown);
    }

    private PipelineStageDispatcher dispatcher(StageConcurrency concurrency, int burstLimit, StageWakeUps wakeUps,
                                               Duration pollInterval) {
        PipelineStageWorker worker = new PipelineStageWorker(engine, payloads, stages, completion, usage, settings,
                concurrency, burstLimit);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(PipelineStageConfig.SCHEDULER_THREADS);
        scheduler.initialize();
        schedulers.add(scheduler);
        ThreadPoolTaskExecutor executor = PipelineStageConfig.stageExecutor(concurrency);
        executors.add(executor);
        PipelineStageDispatcher dispatcher = new PipelineStageDispatcher(worker, engine, wakeUps, concurrency, scheduler,
                executor, pollInterval, pollInterval, Clock.systemUTC());
        dispatchers.add(dispatcher);
        return dispatcher;
    }

    private PipelineStageDispatcher earlierBehavior() {
        return dispatcher(StageConcurrency.uniform(SINGLE_SLOT), SINGLE_JOB_BURST, new StageWakeUps(), SHORT_POLL);
    }

    private PipelineStageDispatcher currentBehavior(Duration pollInterval) {
        return dispatcher(StageConcurrency.defaults(), PipelineStageWorker.DEFAULT_BURST_LIMIT, engineWakeUps, pollInterval);
    }

    private void startRuns(String prefix) {
        IntStream.range(0, SONG_COUNT).forEach(index -> engine.startRun("%s%05d".formatted(prefix, index),
                PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, null, null));
    }

    private long finishedRuns() {
        return core.queryForObject("SELECT count(*) FROM pipeline_runs WHERE status = 'DONE'", Long.class);
    }

    private Duration timeUntilRunsFinish(long expectedRuns, long startedAtNanos) throws InterruptedException {
        long deadline = startedAtNanos + FINISH_TIMEOUT.toNanos();
        while (finishedRuns() < expectedRuns) {
            assertThat(System.nanoTime()).as("runs finish before the timeout").isLessThan(deadline);
            Thread.sleep(CHECK_PAUSE.toMillis());
        }
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    private Duration drainWith(PipelineStageDispatcher dispatcher, String prefix) throws InterruptedException {
        startRuns(prefix);
        long startedAt = System.nanoTime();
        dispatcher.start();
        Duration elapsed = timeUntilRunsFinish(SONG_COUNT, startedAt);
        dispatcher.stop();
        return elapsed;
    }

    @Test
    void parallelSlotsBurstsAndWakeUpsFinishSongsMuchFasterThanOneJobPerPoll() throws Exception {
        Duration earlier = drainWith(earlierBehavior(), "earlier");
        assertThat(stages.mostConcurrentCalls(PipelineStage.YOUTUBE_METADATA)).isEqualTo(SINGLE_SLOT);
        core.execute("TRUNCATE songs, pipeline_runs RESTART IDENTITY CASCADE");
        stages.reset();

        Duration current = drainWith(currentBehavior(SHORT_POLL), "current");
        LOGGER.info("{} songs: one job per poll took {} ms, the dispatcher took {} ms", SONG_COUNT, earlier.toMillis(),
                current.toMillis());

        assertThat(songs.count()).isEqualTo(SONG_COUNT);
        assertThat(current.multipliedBy(REQUIRED_SPEEDUP)).isLessThan(earlier);
        assertThat(stages.mostConcurrentCalls(PipelineStage.YOUTUBE_METADATA))
                .isGreaterThan(SINGLE_SLOT).isLessThanOrEqualTo(StageConcurrency.DEFAULT_YOUTUBE_METADATA_LIMIT);
        assertThat(stages.mostConcurrentCalls(PipelineStage.MUSICBRAINZ))
                .isLessThanOrEqualTo(StageConcurrency.DEFAULT_SOURCE_LIMIT);
    }

    @Test
    void aNewRunAndEveryFollowingStageStartOnAWakeUpLongBeforeTheNextPoll() throws Exception {
        PipelineStageDispatcher dispatcher = currentBehavior(LONG_POLL);
        dispatcher.start();
        long startedAt = System.nanoTime();
        engine.startRun("wakeUp00001", PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, null, null);

        Duration elapsed = timeUntilRunsFinish(SINGLE_SLOT, startedAt);
        assertThat(elapsed).isLessThan(WAKE_UP_BOUND);
        assertThat(stages.calls()).containsOnlyKeys(PipelineStage.YOUTUBE_METADATA, PipelineStage.MUSICBRAINZ,
                PipelineStage.DISCOGS, PipelineStage.WIKIDATA);
    }

    @Test
    void anIdleDispatcherOnlyReadsTheDatabaseOncePerIdleInterval() throws Exception {
        PipelineJobService countingEngine = org.mockito.Mockito.spy(engine);
        PipelineStageWorker worker = new PipelineStageWorker(countingEngine, payloads, stages, completion, usage, settings,
                StageConcurrency.defaults(), PipelineStageWorker.DEFAULT_BURST_LIMIT);
        MutableClock clock = new MutableClock(java.time.Instant.now());
        PipelineStageDispatcher dispatcher = new PipelineStageDispatcher(worker, countingEngine, new StageWakeUps(),
                StageConcurrency.defaults(), mock(org.springframework.scheduling.TaskScheduler.class), Runnable::run,
                SHORT_POLL, LONG_POLL, clock);
        dispatcher.start();

        dispatcher.poll();
        clock.advance(SHORT_POLL);
        dispatcher.poll();
        clock.advance(SHORT_POLL);
        dispatcher.poll();
        org.mockito.Mockito.verify(countingEngine, org.mockito.Mockito.times(1)).stagesWithDueWork();

        clock.advance(LONG_POLL);
        dispatcher.poll();
        org.mockito.Mockito.verify(countingEngine, org.mockito.Mockito.times(2)).stagesWithDueWork();

        dispatcher.wake(PipelineStage.MUSICBRAINZ);
        clock.advance(SHORT_POLL);
        dispatcher.poll();
        org.mockito.Mockito.verify(countingEngine, org.mockito.Mockito.times(3)).stagesWithDueWork();
        dispatcher.stop();
    }

    static class SlowStages implements AiStageClient {
        final ObjectMapper mapper;
        private final Map<PipelineStage, AtomicInteger> callCounts = new ConcurrentHashMap<>();
        private final Map<PipelineStage, AtomicInteger> inFlight = new ConcurrentHashMap<>();
        private final Map<PipelineStage, AtomicInteger> mostInFlight = new ConcurrentHashMap<>();

        SlowStages(ObjectMapper mapper) { this.mapper = mapper; }

        void reset() {
            callCounts.clear();
            inFlight.clear();
            mostInFlight.clear();
        }

        Map<PipelineStage, AtomicInteger> calls() { return callCounts; }

        int mostConcurrentCalls(PipelineStage stage) {
            return mostInFlight.getOrDefault(stage, new AtomicInteger()).get();
        }

        @Override
        public JsonNode execute(PipelineStage stage, JsonNode request) {
            callCounts.computeIfAbsent(stage, ignored -> new AtomicInteger()).incrementAndGet();
            int running = inFlight.computeIfAbsent(stage, ignored -> new AtomicInteger()).incrementAndGet();
            mostInFlight.computeIfAbsent(stage, ignored -> new AtomicInteger()).accumulateAndGet(running, Math::max);
            try {
                Thread.sleep((stage == PipelineStage.YOUTUBE_METADATA ? YOUTUBE_CALL : SOURCE_CALL).toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while simulating a stage call", interrupted);
            } finally {
                inFlight.get(stage).decrementAndGet();
            }
            return switch (stage) {
                case YOUTUBE_METADATA -> mapper.readTree("""
                        {"outcome":"IDENTIFIED","title":"Song %s","artists":["Test artist"],
                         "featuredArtists":[],"color":"112233","video":{"durationSeconds":215}}
                        """.formatted(request.path("youtubeId").asString()));
                case MUSICBRAINZ, DISCOGS, WIKIDATA -> mapper.createObjectNode().put("earliestYear", YEAR)
                        .set("candidates", mapper.createArrayNode());
                default -> throw new IllegalArgumentException("Stage is not used by agreeing sources");
            };
        }
    }
}
