package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the engine against the real migrations on Postgres: run creation under concurrency,
 * leasing order and exclusivity, lease reclaim, retries and failures, the full stage graph,
 * and the control operations.
 */
@Testcontainers
class PipelineJobServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static final Instant START = Instant.parse("2026-10-16T12:00:00Z");
    private static final Duration LEASE = PipelineJobService.DEFAULT_LEASE_DURATION;
    private static final Duration PAST_THE_LEASE = LEASE.plusMinutes(1);
    private static final Duration PAST_ANY_BACKOFF = RetryBackoff.MAX_DELAY.plusMinutes(1);
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final int SMALL_MAX_ATTEMPTS = 3;
    private static final int CONCURRENT_CALLERS = 8;
    private static final int CONCURRENT_JOB_COUNT = 40;
    private static final long WAIT_SECONDS = 60;
    private static final String WORKER = "worker-a";
    private static final String OTHER_WORKER = "worker-b";
    private static final String VIDEO_ID = "videoOne001";
    private static final String OTHER_VIDEO_ID = "videoTwo002";
    private static final String THIRD_VIDEO_ID = "videoThree3";
    private static final String TIMEOUT_REASON = "MusicBrainz timed out";
    private static final String KEY_REASON = "YouTube answered 403";
    private static final String GONE_REASON = "Video unavailable";
    private static final String EMPTY_RESULT = "{}";
    private static final int AGREED_YEAR = 1984;
    private static final int OTHER_YEAR = 1991;
    private static final Integer PROVISIONAL_YEAR = 1990;

    private static JdbcTemplate jdbcTemplate;
    private static PipelineJobRepository repository;
    private static JdbcTransactionManager transactionManager;

    private MutableClock clock;
    private PipelineJobService service;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new PipelineJobRepository(jdbcTemplate);
        transactionManager = new JdbcTransactionManager(dataSource);
    }

    @BeforeEach
    void resetTablesAndClock() {
        jdbcTemplate.update("DELETE FROM pipeline_jobs");
        jdbcTemplate.update("DELETE FROM pipeline_runs");
        jdbcTemplate.update("DELETE FROM import_batches");
        jdbcTemplate.update("UPDATE stage_controls SET paused = false, paused_reason = NULL, paused_at = NULL");
        clock = new MutableClock(START);
        service = serviceWithMaxAttempts(PipelineJobService.DEFAULT_MAX_ATTEMPTS);
    }

    private PipelineJobService serviceWithMaxAttempts(int maxAttempts) {
        return new PipelineJobService(repository, transactionManager, maxAttempts, clock, new RetryBackoff(() -> 0.0));
    }

    private PipelineRun startBackfill(String youtubeId) {
        return service.startRun(youtubeId, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED, null, null);
    }

    private PipelineJob lease(PipelineStage stage) {
        return service.leaseNext(stage, WORKER, LEASE).orElseThrow();
    }

    private PipelineJob job(long runId, PipelineStage stage) {
        return service.jobsOfRun(runId).stream().filter(job -> job.stage() == stage).findFirst().orElseThrow();
    }

    private Map<PipelineStage, PipelineJobStatus> statusesByStage(long runId) {
        return service.jobsOfRun(runId).stream()
                .collect(Collectors.toMap(PipelineJob::stage, PipelineJob::status));
    }

    private PipelineRun run(long runId) {
        return service.findRun(runId).orElseThrow();
    }

    private static String yearResult(int earliestYear) {
        return "{\"earliestYear\": " + earliestYear + "}";
    }

    private void completeNext(PipelineStage stage, String resultJson) {
        service.complete(lease(stage).id(), WORKER, resultJson);
    }

    private static <T> List<T> runConcurrently(int callerCount, Callable<T> work) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(callerCount);
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int caller = 0; caller < callerCount; caller++) {
                futures.add(executor.submit(() -> {
                    startTogether.await(WAIT_SECONDS, TimeUnit.SECONDS);
                    return work.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void theMigrationSeedsOneUnpausedControlPerStage() {
        List<String> stages = jdbcTemplate.queryForList(
                "SELECT stage FROM stage_controls WHERE NOT paused ORDER BY stage", String.class);

        assertThat(stages).containsExactlyInAnyOrder(
                Arrays.stream(PipelineStage.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void concurrentExclusiveLeasesAllowOnlyOneInProgressJobPerStage() throws Exception {
        startBackfill(VIDEO_ID);
        startBackfill(OTHER_VIDEO_ID);
        List<Optional<PipelineJob>> leases = runConcurrently(CONCURRENT_CALLERS,
                () -> service.leaseExclusive(PipelineStage.YOUTUBE_METADATA, WORKER, true));
        assertThat(leases.stream().filter(Optional::isPresent).toList()).hasSize(1);
    }

    @Test
    void theSchemaRejectsUnknownStatusesAndASecondActiveRunForOneVideo() {
        PipelineRun run = startBackfill(VIDEO_ID);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE pipeline_jobs SET status = 'LOST' WHERE run_id = ?",
                run.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO pipeline_runs (youtube_id, lane, status, origin) VALUES (?, 'BACKFILL', 'ACTIVE', 'ADMIN_SEED')",
                VIDEO_ID)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theLeaseQueryUsesThePartialLeaseOrderIndex() {
        startBackfill(VIDEO_ID);
        List<String> plan = new TransactionTemplate(transactionManager)
                .execute(status -> {
                    jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
                    return jdbcTemplate.queryForList("EXPLAIN " + PipelineJobRepository.LEASE_NEXT_SQL, String.class,
                            PipelineStage.YOUTUBE_METADATA.name(), OffsetDateTime.now(), true,
                            PipelineJobStatus.IN_PROGRESS.name(), WORKER, OffsetDateTime.now(), OffsetDateTime.now(),
                            OffsetDateTime.now());
                });

        assertThat(String.join("\n", plan)).contains("pipeline_jobs_waiting_lease_order");
    }

    @Test
    void startingARunCreatesItsMetadataJobAndReturnsTheActiveRunWhenStartedAgain() {
        long batchId = service.createBatch(ImportBatchKind.ADMIN_IDS, "Pasted list", null, null);
        PipelineRun first = service.startRun(VIDEO_ID, PipelineLane.USER_IMPORT, PendingImportOrigin.USER_ADD_RECHECK,
                batchId, PROVISIONAL_YEAR);
        PipelineRun second = startBackfill(VIDEO_ID);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(first.batchId()).isEqualTo(batchId);
        assertThat(first.origin()).isEqualTo(PendingImportOrigin.USER_ADD_RECHECK);
        assertThat(first.provisionalYear()).isEqualTo(PROVISIONAL_YEAR);
        List<PipelineJob> jobs = service.jobsOfRun(first.id());
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.stage()).isEqualTo(PipelineStage.YOUTUBE_METADATA);
            assertThat(job.status()).isEqualTo(PipelineJobStatus.WAITING);
            assertThat(job.lane()).isEqualTo(PipelineLane.USER_IMPORT);
            assertThat(job.maxAttempts()).isEqualTo(PipelineJobService.DEFAULT_MAX_ATTEMPTS);
        });
    }

    @Test
    void concurrentStartsOfOneVideoCreateOneActiveRun() throws Exception {
        List<PipelineRun> runs = runConcurrently(CONCURRENT_CALLERS, () -> startBackfill(VIDEO_ID));

        assertThat(runs.stream().map(PipelineRun::id).distinct()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pipeline_jobs", Integer.class)).isOne();
    }

    @Test
    void concurrentWorkersNeverLeaseTheSameJob() throws Exception {
        IntStream.range(0, CONCURRENT_JOB_COUNT).forEach(index -> startBackfill("concurrent" + index));
        List<Long> leasedJobIds = new CopyOnWriteArrayList<>();

        runConcurrently(CONCURRENT_CALLERS, () -> {
            String workerName = "worker-" + Thread.currentThread().threadId();
            Optional<PipelineJob> leased = service.leaseNext(PipelineStage.YOUTUBE_METADATA, workerName, LEASE);
            while (leased.isPresent()) {
                leasedJobIds.add(leased.get().id());
                leased = service.leaseNext(PipelineStage.YOUTUBE_METADATA, workerName, LEASE);
            }
            return null;
        });

        assertThat(leasedJobIds).hasSize(CONCURRENT_JOB_COUNT).doesNotHaveDuplicates();
    }

    @Test
    void userImportsLeaseFirstThenHigherPriorityThenOldest() {
        PipelineRun oldestBackfill = startBackfill(VIDEO_ID);
        clock.advance(ONE_SECOND);
        PipelineRun bumpedBackfill = startBackfill(OTHER_VIDEO_ID);
        clock.advance(ONE_SECOND);
        PipelineRun newestUserImport = service.startRun(THIRD_VIDEO_ID, PipelineLane.USER_IMPORT,
                PendingImportOrigin.USER_ADD_RECHECK, null, null);
        assertThat(service.bump(bumpedBackfill.id())).isOne();

        List<Long> leaseOrder = List.of(lease(PipelineStage.YOUTUBE_METADATA).runId(),
                lease(PipelineStage.YOUTUBE_METADATA).runId(), lease(PipelineStage.YOUTUBE_METADATA).runId());

        assertThat(leaseOrder).containsExactly(newestUserImport.id(), bumpedBackfill.id(), oldestBackfill.id());
        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, WORKER, LEASE)).isEmpty();
    }

    @Test
    void aPausedStageLeasesNothingUntilResumed() {
        startBackfill(VIDEO_ID);
        service.setStagePaused(PipelineStage.YOUTUBE_METADATA, true, "Maintenance");

        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, WORKER, LEASE)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT paused_reason FROM stage_controls WHERE stage = ?",
                String.class, PipelineStage.YOUTUBE_METADATA.name())).isEqualTo("Maintenance");

        service.setStagePaused(PipelineStage.YOUTUBE_METADATA, false, null);

        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, WORKER, LEASE)).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT paused_reason FROM stage_controls WHERE stage = ?",
                String.class, PipelineStage.YOUTUBE_METADATA.name())).isNull();
    }

    @Test
    void aLeasedJobCarriesItsLeaseAndIsNotLeasedAgainWhileHeld() {
        startBackfill(VIDEO_ID);
        PipelineJob leased = lease(PipelineStage.YOUTUBE_METADATA);

        assertThat(leased.status()).isEqualTo(PipelineJobStatus.IN_PROGRESS);
        assertThat(leased.leaseOwner()).isEqualTo(WORKER);
        assertThat(leased.leaseExpiresAt()).isEqualTo(START.plus(LEASE));
        assertThat(leased.startedAt()).isEqualTo(START);
        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, OTHER_WORKER, LEASE)).isEmpty();
    }

    @Test
    void anExpiredLeaseIsReclaimedInsideLeaseNextAndCountsAsAnAttempt() {
        PipelineRun run = startBackfill(VIDEO_ID);
        PipelineJob lostJob = lease(PipelineStage.YOUTUBE_METADATA);
        clock.advance(PAST_THE_LEASE);

        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, OTHER_WORKER, LEASE)).isEmpty();
        PipelineJob reclaimed = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(reclaimed.status()).isEqualTo(PipelineJobStatus.WAITING);
        assertThat(reclaimed.attempts()).isOne();
        assertThat(reclaimed.failureReason()).isEqualTo(PipelineJobService.LEASE_EXPIRED_REASON);
        assertThat(reclaimed.nextAttemptAt()).isEqualTo(clock.instant().plus(RetryBackoff.BASE_DELAY));

        clock.advance(RetryBackoff.BASE_DELAY);
        PipelineJob retaken = service.leaseNext(PipelineStage.YOUTUBE_METADATA, OTHER_WORKER, LEASE).orElseThrow();

        assertThat(retaken.id()).isEqualTo(lostJob.id());
        assertThatThrownBy(() -> service.complete(lostJob.id(), WORKER, EMPTY_RESULT))
                .isInstanceOf(LeaseNotHeldException.class);
    }

    @Test
    void aJobThatKeepsLosingItsLeaseEventuallyFailsTheRun() {
        service = serviceWithMaxAttempts(SMALL_MAX_ATTEMPTS);
        PipelineRun run = startBackfill(VIDEO_ID);
        for (int attempt = 0; attempt < SMALL_MAX_ATTEMPTS; attempt++) {
            lease(PipelineStage.YOUTUBE_METADATA);
            clock.advance(PAST_THE_LEASE);
            assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, OTHER_WORKER, LEASE)).isEmpty();
            clock.advance(PAST_ANY_BACKOFF);
        }

        PipelineJob failed = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(failed.status()).isEqualTo(PipelineJobStatus.FAILED);
        assertThat(failed.failureReason())
                .isEqualTo("Gave up after 3 attempts: " + PipelineJobService.LEASE_EXPIRED_REASON);
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.FAILED);
    }

    @Test
    void aTransientFailureSchedulesARetryAfterTheBackoffAndUsesAnAttempt() {
        PipelineRun run = startBackfill(VIDEO_ID);
        PipelineJob leased = lease(PipelineStage.YOUTUBE_METADATA);
        clock.advance(ONE_SECOND);

        service.failTransient(leased.id(), WORKER, TIMEOUT_REASON, false);

        PipelineJob retrying = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(retrying.status()).isEqualTo(PipelineJobStatus.WAITING);
        assertThat(retrying.attempts()).isOne();
        assertThat(retrying.nextAttemptAt()).isEqualTo(START.plus(ONE_SECOND).plus(RetryBackoff.BASE_DELAY));
        assertThat(retrying.failureKind()).isEqualTo(FailureKind.TRANSIENT);
        assertThat(retrying.failureStage()).isEqualTo(PipelineStage.YOUTUBE_METADATA);
        assertThat(retrying.failureReason()).isEqualTo(TIMEOUT_REASON);
        assertThat(retrying.leaseOwner()).isNull();
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, WORKER, LEASE)).isEmpty();

        clock.advance(RetryBackoff.BASE_DELAY);
        PipelineJob secondTry = lease(PipelineStage.YOUTUBE_METADATA);
        clock.advance(ONE_SECOND);
        service.failTransient(secondTry.id(), WORKER, TIMEOUT_REASON, false);

        assertThat(job(run.id(), PipelineStage.YOUTUBE_METADATA).nextAttemptAt())
                .isEqualTo(clock.instant().plus(RetryBackoff.BASE_DELAY.multipliedBy(2)));
    }

    @Test
    void runningOutOfAttemptsFailsTheJobAndTheRunWithTheReason() {
        service = serviceWithMaxAttempts(SMALL_MAX_ATTEMPTS);
        PipelineRun run = startBackfill(VIDEO_ID);
        for (int attempt = 0; attempt < SMALL_MAX_ATTEMPTS; attempt++) {
            service.failTransient(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, TIMEOUT_REASON, false);
            clock.advance(PAST_ANY_BACKOFF);
        }

        PipelineJob failed = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(failed.status()).isEqualTo(PipelineJobStatus.FAILED);
        assertThat(failed.attempts()).isEqualTo(SMALL_MAX_ATTEMPTS);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.TRANSIENT);
        assertThat(failed.failureReason()).isEqualTo("Gave up after 3 attempts: " + TIMEOUT_REASON);
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(run(run.id()).finishedAt()).isNotNull();
    }

    @Test
    void aRejectedKeyPausesTheStageWithoutUsingAnAttemptOrFailingTheRun() {
        PipelineRun run = startBackfill(VIDEO_ID);
        startBackfill(OTHER_VIDEO_ID);

        service.failTransient(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, KEY_REASON, true);

        PipelineJob returned = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(returned.status()).isEqualTo(PipelineJobStatus.WAITING);
        assertThat(returned.attempts()).isZero();
        assertThat(returned.failureReason()).isEqualTo(KEY_REASON);
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(jdbcTemplate.queryForMap("SELECT paused, paused_reason FROM stage_controls WHERE stage = ?",
                PipelineStage.YOUTUBE_METADATA.name()))
                .containsEntry("paused", true)
                .containsEntry("paused_reason", PipelineJobService.KEY_REJECTED_PAUSE_REASON);
        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, WORKER, LEASE)).isEmpty();
    }

    @Test
    void aPermanentFailureFailsTheJobAndTheRunAtOnce() {
        PipelineRun run = startBackfill(VIDEO_ID);

        service.failPermanent(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, GONE_REASON);

        PipelineJob failed = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(failed.status()).isEqualTo(PipelineJobStatus.FAILED);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.PERMANENT);
        assertThat(failed.failureStage()).isEqualTo(PipelineStage.YOUTUBE_METADATA);
        assertThat(failed.failureReason()).isEqualTo(GONE_REASON);
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.FAILED);
    }

    @Test
    void aWorkerWithoutTheLeaseCannotCompleteOrFailTheJob() {
        PipelineRun run = startBackfill(VIDEO_ID);
        long jobId = lease(PipelineStage.YOUTUBE_METADATA).id();

        assertThatThrownBy(() -> service.complete(jobId, OTHER_WORKER, EMPTY_RESULT))
                .isInstanceOf(LeaseNotHeldException.class);
        assertThatThrownBy(() -> service.failTransient(jobId, OTHER_WORKER, TIMEOUT_REASON, false))
                .isInstanceOf(LeaseNotHeldException.class);
        assertThatThrownBy(() -> service.failPermanent(jobId, OTHER_WORKER, GONE_REASON))
                .isInstanceOf(LeaseNotHeldException.class);
        PipelineJob untouched = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(untouched.status()).isEqualTo(PipelineJobStatus.IN_PROGRESS);
        assertThat(untouched.leaseOwner()).isEqualTo(WORKER);

        service.complete(jobId, WORKER, EMPTY_RESULT);
        assertThatThrownBy(() -> service.complete(jobId, WORKER, EMPTY_RESULT))
                .isInstanceOf(LeaseNotHeldException.class);
    }

    @Test
    void agreeingSourcesSkipWikipediaAndReconciliationOnAUserImportRun() {
        PipelineRun run = service.startRun(VIDEO_ID, PipelineLane.USER_IMPORT, PendingImportOrigin.USER_ADD_RECHECK,
                null, null);

        completeNext(PipelineStage.YOUTUBE_METADATA, EMPTY_RESULT);
        assertThat(statusesByStage(run.id())).containsOnlyKeys(PipelineStage.YOUTUBE_METADATA,
                PipelineStage.MUSICBRAINZ, PipelineStage.DISCOGS, PipelineStage.WIKIDATA);
        completeNext(PipelineStage.MUSICBRAINZ, yearResult(AGREED_YEAR));
        completeNext(PipelineStage.DISCOGS, yearResult(AGREED_YEAR));
        assertThat(run(run.id()).readyToSaveAt()).isNull();
        completeNext(PipelineStage.WIKIDATA, yearResult(AGREED_YEAR));

        assertThat(run(run.id()).readyToSaveAt()).isEqualTo(START);
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(service.leaseNext(PipelineStage.WIKIPEDIA, WORKER, LEASE)).isEmpty();
        assertThat(service.leaseNext(PipelineStage.LLM_RECONCILIATION, WORKER, LEASE)).isEmpty();

        assertThat(statusesByStage(run.id())).isEqualTo(Map.of(
                PipelineStage.YOUTUBE_METADATA, PipelineJobStatus.DONE,
                PipelineStage.MUSICBRAINZ, PipelineJobStatus.DONE,
                PipelineStage.DISCOGS, PipelineJobStatus.DONE,
                PipelineStage.WIKIDATA, PipelineJobStatus.DONE,
                PipelineStage.WIKIPEDIA, PipelineJobStatus.SKIPPED,
                PipelineStage.LLM_RECONCILIATION, PipelineJobStatus.SKIPPED));
        assertThat(service.jobsOfRun(run.id())).allSatisfy(job -> assertThat(job.lane()).isEqualTo(PipelineLane.USER_IMPORT));
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(job(run.id(), PipelineStage.WIKIDATA).resultJson()).contains(String.valueOf(AGREED_YEAR));
    }

    @Test
    void disagreeingSourcesRunAllMetadataStagesAndWaitForPersistence() {
        PipelineRun run = startBackfill(VIDEO_ID);

        completeNext(PipelineStage.YOUTUBE_METADATA, EMPTY_RESULT);
        completeNext(PipelineStage.MUSICBRAINZ, yearResult(AGREED_YEAR));
        completeNext(PipelineStage.DISCOGS, yearResult(OTHER_YEAR));
        completeNext(PipelineStage.WIKIDATA, "{\"earliestYear\": null}");
        assertThat(run(run.id()).readyToSaveAt()).isNull();
        completeNext(PipelineStage.WIKIPEDIA, yearResult(AGREED_YEAR));
        completeNext(PipelineStage.LLM_RECONCILIATION, yearResult(AGREED_YEAR));
        assertThat(run(run.id()).readyToSaveAt()).isNotNull();

        assertThat(statusesByStage(run.id())).hasSize(HttpAiStageClient.PATHS.size())
                .allSatisfy((stage, status) -> assertThat(status).isEqualTo(PipelineJobStatus.DONE));
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.ACTIVE);
    }

    @Test
    void sourcesFinishingAtTheSameMomentStillQueueWikipediaOnce() throws Exception {
        PipelineRun run = startBackfill(VIDEO_ID);
        completeNext(PipelineStage.YOUTUBE_METADATA, EMPTY_RESULT);
        List<PipelineJob> sourceJobs = List.of(lease(PipelineStage.MUSICBRAINZ), lease(PipelineStage.DISCOGS),
                lease(PipelineStage.WIKIDATA));
        Map<PipelineStage, Integer> yearsByStage = Map.of(PipelineStage.MUSICBRAINZ, AGREED_YEAR,
                PipelineStage.DISCOGS, OTHER_YEAR, PipelineStage.WIKIDATA, AGREED_YEAR);
        ConcurrentLinkedQueue<PipelineJob> unreportedJobs = new ConcurrentLinkedQueue<>(sourceJobs);

        runConcurrently(sourceJobs.size(), () -> {
            PipelineJob sourceJob = unreportedJobs.remove();
            service.complete(sourceJob.id(), WORKER, yearResult(yearsByStage.get(sourceJob.stage())));
            return null;
        });

        assertThat(statusesByStage(run.id())).containsEntry(PipelineStage.WIKIPEDIA, PipelineJobStatus.WAITING);
    }

    @Test
    void cancellingABatchCancelsItsRunsAndWaitingJobsAndDropsLateResults() {
        long batchId = service.createBatch(ImportBatchKind.ADMIN_PLAYLIST, "Eighties", "https://example.test/list", null);
        PipelineRun leasedRun = service.startRun(VIDEO_ID, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED,
                batchId, null);
        PipelineRun waitingRun = service.startRun(OTHER_VIDEO_ID, PipelineLane.BACKFILL, PendingImportOrigin.ADMIN_SEED,
                batchId, null);
        PipelineRun unrelatedRun = startBackfill(THIRD_VIDEO_ID);
        PipelineJob inProgress = service.leaseNext(PipelineStage.YOUTUBE_METADATA, WORKER, LEASE).orElseThrow();
        assertThat(inProgress.runId()).isEqualTo(leasedRun.id());

        assertThat(service.cancelBatch(batchId)).isEqualTo(2);

        assertThat(run(leasedRun.id()).status()).isEqualTo(PipelineRunStatus.CANCELLED);
        assertThat(run(waitingRun.id()).status()).isEqualTo(PipelineRunStatus.CANCELLED);
        assertThat(job(waitingRun.id(), PipelineStage.YOUTUBE_METADATA).status()).isEqualTo(PipelineJobStatus.CANCELLED);
        assertThat(job(leasedRun.id(), PipelineStage.YOUTUBE_METADATA).status()).isEqualTo(PipelineJobStatus.IN_PROGRESS);
        assertThat(jdbcTemplate.queryForObject("SELECT cancelled_at IS NOT NULL FROM import_batches WHERE id = ?",
                Boolean.class, batchId)).isTrue();

        service.complete(inProgress.id(), WORKER, EMPTY_RESULT);

        assertThat(statusesByStage(leasedRun.id()))
                .isEqualTo(Map.of(PipelineStage.YOUTUBE_METADATA, PipelineJobStatus.CANCELLED));
        assertThat(run(unrelatedRun.id()).status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(lease(PipelineStage.YOUTUBE_METADATA).runId()).isEqualTo(unrelatedRun.id());
    }

    @Test
    void retryingAFailedRunRequeuesTheFailedJobWithFreshAttempts() {
        PipelineRun run = startBackfill(VIDEO_ID);
        service.failPermanent(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, GONE_REASON);
        assertThat(service.retryFailed(run.id())).isTrue();

        PipelineJob requeued = job(run.id(), PipelineStage.YOUTUBE_METADATA);
        assertThat(requeued.status()).isEqualTo(PipelineJobStatus.WAITING);
        assertThat(requeued.attempts()).isZero();
        assertThat(requeued.failureReason()).isNull();
        assertThat(requeued.failureKind()).isNull();
        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.ACTIVE);
        assertThat(run(run.id()).finishedAt()).isNull();
        assertThat(lease(PipelineStage.YOUTUBE_METADATA).id()).isEqualTo(requeued.id());
        assertThat(service.retryFailed(run.id())).isFalse();
    }

    @Test
    void aFailedRunIsNotRetriedWhileAnotherRunOfTheVideoIsActive() {
        PipelineRun failedRun = startBackfill(VIDEO_ID);
        service.failPermanent(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, GONE_REASON);
        PipelineRun newerRun = startBackfill(VIDEO_ID);

        assertThat(newerRun.id()).isNotEqualTo(failedRun.id());
        assertThat(service.retryFailed(failedRun.id())).isFalse();
        assertThat(run(failedRun.id()).status()).isEqualTo(PipelineRunStatus.FAILED);
    }

    @Test
    void discardingARunCancelsItAndItsWaitingJobs() {
        PipelineRun run = startBackfill(VIDEO_ID);

        assertThat(service.discardRun(run.id())).isTrue();

        assertThat(run(run.id()).status()).isEqualTo(PipelineRunStatus.CANCELLED);
        assertThat(job(run.id(), PipelineStage.YOUTUBE_METADATA).status()).isEqualTo(PipelineJobStatus.CANCELLED);
        assertThat(service.leaseNext(PipelineStage.YOUTUBE_METADATA, WORKER, LEASE)).isEmpty();
        assertThat(service.discardRun(run.id())).isFalse();
    }

    @Test
    void aBumpedRunKeepsItsPriorityInTheStagesItReachesLater() {
        startBackfill(VIDEO_ID);
        clock.advance(ONE_SECOND);
        PipelineRun bumped = startBackfill(OTHER_VIDEO_ID);
        service.bump(bumped.id());

        PipelineJob firstLeased = lease(PipelineStage.YOUTUBE_METADATA);
        assertThat(firstLeased.runId()).isEqualTo(bumped.id());
        service.complete(firstLeased.id(), WORKER, EMPTY_RESULT);
        completeNext(PipelineStage.YOUTUBE_METADATA, EMPTY_RESULT);

        assertThat(lease(PipelineStage.MUSICBRAINZ).runId()).isEqualTo(bumped.id());
        assertThat(job(bumped.id(), PipelineStage.DISCOGS).priority()).isPositive();
    }

    @Test
    void stageCountsReportEveryStageInOneRead() {
        startBackfill(VIDEO_ID);
        startBackfill(OTHER_VIDEO_ID);
        startBackfill(THIRD_VIDEO_ID);
        startBackfill("countsFour4");
        startBackfill("countsFive5");
        service.complete(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, EMPTY_RESULT);
        service.failPermanent(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, GONE_REASON);
        service.failTransient(lease(PipelineStage.YOUTUBE_METADATA).id(), WORKER, TIMEOUT_REASON, false);
        lease(PipelineStage.YOUTUBE_METADATA);

        Map<PipelineStage, StageCounts> countsByStage = service.stageCounts().stream()
                .collect(Collectors.toMap(StageCounts::stage, Function.identity()));

        assertThat(countsByStage).hasSize(PipelineStage.values().length);
        assertThat(countsByStage.get(PipelineStage.YOUTUBE_METADATA))
                .isEqualTo(new StageCounts(PipelineStage.YOUTUBE_METADATA, false, 2, 1, 1, 1, 1));
        assertThat(countsByStage.get(PipelineStage.MUSICBRAINZ))
                .isEqualTo(new StageCounts(PipelineStage.MUSICBRAINZ, false, 1, 0, 0, 0, 0));
        assertThat(countsByStage.get(PipelineStage.EMBEDDING))
                .isEqualTo(new StageCounts(PipelineStage.EMBEDDING, false, 0, 0, 0, 0, 0));

        clock.advance(ONE_DAY);
        assertThat(service.stageCounts().stream()
                .filter(counts -> counts.stage() == PipelineStage.YOUTUBE_METADATA)
                .findFirst().orElseThrow().doneToday()).isZero();
    }
}
