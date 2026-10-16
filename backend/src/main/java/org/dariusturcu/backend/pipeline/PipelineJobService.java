package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The persisted job engine of the stage pipeline. Each operation runs in its own transaction.
 * Expired leases are reclaimed lazily inside {@link #leaseNext}, never by a background poll,
 * and a lost lease counts as a failed attempt so a job that keeps crashing its worker still
 * gives up. A rejected API key pauses the whole stage and costs the job no attempt.
 */
@Service
public class PipelineJobService {

    public static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
    public static final int DEFAULT_MAX_ATTEMPTS = 6;
    static final int DEFAULT_PRIORITY = 0;
    static final String KEY_REJECTED_PAUSE_REASON = "API key rejected";
    static final String LEASE_EXPIRED_REASON = "Lease expired before the worker reported back";
    private static final String GAVE_UP_REASON_FORMAT = "Gave up after %d attempts: %s";

    private final PipelineJobRepository repository;
    private final TransactionTemplate transaction;
    private final int maxAttempts;
    private final Clock clock;
    private final RetryBackoff retryBackoff;

    @Autowired
    public PipelineJobService(PipelineJobRepository repository, PlatformTransactionManager transactionManager,
                              @Value("${pipeline.job.max-attempts:" + DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        this(repository, transactionManager, maxAttempts, Clock.systemUTC(), new RetryBackoff());
    }

    PipelineJobService(PipelineJobRepository repository, PlatformTransactionManager transactionManager,
                       int maxAttempts, Clock clock, RetryBackoff retryBackoff) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.retryBackoff = retryBackoff;
    }

    public long createBatch(ImportBatchKind kind, String label, String sourceLink, Long createdBy) {
        return inTransaction(() -> repository.insertBatch(kind, label, sourceLink, createdBy, clock.instant()));
    }

    /**
     * Starts a run with its YouTube metadata job, or returns the run already active for the
     * same YouTube ID. Later stages are created as results arrive.
     */
    public PipelineRun startRun(String youtubeId, PipelineLane lane, PendingImportOrigin origin, Long batchId,
                                Integer provisionalYear) {
        return inTransaction(() -> {
            repository.lockVideo(youtubeId);
            Optional<PipelineRun> activeRun = repository.findActiveRun(youtubeId);
            if (activeRun.isPresent()) {
                if (lane == PipelineLane.USER_IMPORT && activeRun.get().lane() == PipelineLane.BACKFILL) {
                    return repository.promoteToUserLane(activeRun.get(), origin, provisionalYear);
                }
                return activeRun.get();
            }
            Instant now = clock.instant();
            PipelineRun run = repository.insertRun(youtubeId, lane, origin, batchId, provisionalYear, now);
            repository.insertJob(run.id(), PipelineStage.YOUTUBE_METADATA, lane, PipelineJobStatus.WAITING,
                    DEFAULT_PRIORITY, maxAttempts, now);
            return run;
        });
    }

    /**
     * Leases the next due job of the stage: user imports first, then higher priority, then
     * oldest. Returns nothing when the stage is paused or has no due job.
     */
    public Optional<PipelineJob> leaseNext(PipelineStage stage, String leaseOwner, Duration leaseDuration) {
        return inTransaction(() -> {
            Instant now = clock.instant();
            for (Long expiredJobId : repository.expiredLeaseJobIds(stage, now)) {
                reclaimExpiredLease(expiredJobId, now);
            }
            return repository.leaseNextWaiting(stage, leaseOwner, now.plus(leaseDuration), now);
        });
    }

    public void complete(long jobId, String leaseOwner, String resultJson) {
        complete(jobId, leaseOwner, resultJson, false);
    }

    public void complete(long jobId, String leaseOwner, String resultJson, boolean finalResult) {
        inTransaction(() -> {
            LeasedJob leased = lockLeasedJob(jobId, leaseOwner);
            Instant now = clock.instant();
            if (leased.run().status() == PipelineRunStatus.CANCELLED) {
                repository.markJobCancelled(jobId, now);
                return null;
            }
            repository.markJobDone(jobId, resultJson, now);
            if (finalResult) {
                repository.markRunReadyToSave(leased.run().id(), now);
            } else if (leased.run().status() == PipelineRunStatus.ACTIVE) {
                applyDependencies(leased.run(), leased.job(), now);
            }
            return null;
        });
    }

    /**
     * Records a transient failure. A rejected key pauses the stage and puts the job back without
     * using an attempt; otherwise the job retries after a backoff until its attempts run out.
     */
    public void failTransient(long jobId, String leaseOwner, String reason, boolean keyRejected) {
        inTransaction(() -> {
            LeasedJob leased = lockLeasedJob(jobId, leaseOwner);
            Instant now = clock.instant();
            if (keyRejected && leased.run().status() != PipelineRunStatus.CANCELLED) {
                PipelineJob job = leased.job();
                repository.scheduleRetry(jobId, job.attempts(), now, reason, now);
                repository.setStagePaused(job.stage(), true, KEY_REJECTED_PAUSE_REASON, now);
                return null;
            }
            recordTransientFailure(leased, reason, now);
            return null;
        });
    }

    public boolean hasDueWork(PipelineStage stage) {
        return repository.hasDueWork(stage, clock.instant());
    }

    public Optional<PipelineJob> leaseExclusive(PipelineStage stage, String owner, boolean allowBackfill) {
        return inTransaction(() -> {
            Instant now = clock.instant();
            for (Long jobId : repository.expiredLeaseJobIds(stage, now)) {
                reclaimExpiredLease(jobId, now);
            }
            if (repository.stageIsBusy(stage)) {
                return Optional.empty();
            }
            return repository.leaseNextWaiting(stage, owner, now.plus(DEFAULT_LEASE_DURATION), now, allowBackfill);
        });
    }

    public void reschedule(long jobId, String owner, Duration delay) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Reschedule delay cannot be negative");
        }
        inTransaction(() -> {
            LeasedJob leased = lockLeasedJob(jobId, owner);
            Instant now = clock.instant();
            if (leased.run().status() == PipelineRunStatus.CANCELLED) {
                repository.markJobCancelled(jobId, now);
            } else {
                repository.scheduleRetry(jobId, leased.job().attempts(), now.plus(delay), "Source pace deferred", now);
            }
            return null;
        });
    }

    public void failPermanent(long jobId, String leaseOwner, String reason) {
        inTransaction(() -> {
            LeasedJob leased = lockLeasedJob(jobId, leaseOwner);
            Instant now = clock.instant();
            if (leased.run().status() == PipelineRunStatus.CANCELLED) {
                repository.markJobCancelled(jobId, now);
                return null;
            }
            repository.markJobFailed(jobId, leased.job().attempts() + 1, FailureKind.PERMANENT, reason, now);
            failRunIfActive(leased.run(), now);
            return null;
        });
    }

    /**
     * Cancels the batch's active runs and their waiting jobs. A job already in progress finishes,
     * and its result is dropped. Returns how many runs were cancelled.
     */
    public int cancelBatch(long batchId) {
        return inTransaction(() -> {
            Instant now = clock.instant();
            repository.markBatchCancelled(batchId, now);
            List<Long> runIds = repository.lockActiveRunsOfBatch(batchId);
            repository.cancelWaitingJobsOfRuns(runIds, now);
            runIds.forEach(runId -> repository.finishRun(runId, PipelineRunStatus.CANCELLED, now));
            return runIds.size();
        });
    }

    /**
     * Puts a failed run's failed jobs back in their queues with fresh attempts and makes the run
     * active again. Refused when the run is not failed or another run of the same YouTube ID is
     * already active.
     */
    public boolean retryFailed(long runId) {
        return inTransaction(() -> {
            Optional<PipelineRun> lockedRun = repository.lockRun(runId);
            if (lockedRun.isEmpty() || lockedRun.get().status() != PipelineRunStatus.FAILED) {
                return false;
            }
            repository.lockVideo(lockedRun.get().youtubeId());
            if (repository.findActiveRun(lockedRun.get().youtubeId()).isPresent()) {
                return false;
            }
            repository.resetFailedJobs(runId, maxAttempts, clock.instant());
            repository.reactivateRun(runId);
            return true;
        });
    }

    /** Cancels an active or failed run and its waiting jobs. Returns false for a finished run. */
    public boolean discardRun(long runId) {
        return inTransaction(() -> {
            Optional<PipelineRun> lockedRun = repository.lockRun(runId);
            if (lockedRun.isEmpty() || lockedRun.get().status() == PipelineRunStatus.DONE
                    || lockedRun.get().status() == PipelineRunStatus.CANCELLED) {
                return false;
            }
            Instant now = clock.instant();
            repository.cancelWaitingJobsOfRuns(List.of(runId), now);
            repository.finishRun(runId, PipelineRunStatus.CANCELLED, now);
            return true;
        });
    }

    /** Raises the run's unfinished jobs above every waiting job of their lane. Returns how many changed. */
    public int bump(long runId) {
        return inTransaction(() -> repository.raisePriorityAboveWaiting(runId, clock.instant()));
    }

    public void setStagePaused(PipelineStage stage, boolean paused, String reason) {
        inTransaction(() -> {
            repository.setStagePaused(stage, paused, reason, clock.instant());
            return null;
        });
    }

    /** Per stage: waiting, in progress, done since the start of the UTC day, failed, and retrying. */
    public List<StageCounts> stageCounts() {
        return inTransaction(() -> repository.stageCounts(clock.instant().truncatedTo(ChronoUnit.DAYS)).stream()
                .map(counts -> counts.stage() == PipelineStage.EMBEDDING ? repository.embeddingCounts() : counts).toList());
    }

    public Optional<PipelineRun> findRun(long runId) {
        return repository.findRun(runId);
    }

    public List<PipelineJob> jobsOfRun(long runId) {
        return repository.jobsOfRun(runId);
    }

    private record LeasedJob(PipelineRun run, PipelineJob job) {
    }

    // Locks the run before the job, the order every path uses, and checks the caller still holds the lease.
    private LeasedJob lockLeasedJob(long jobId, String leaseOwner) {
        PipelineJob unlockedJob = repository.findJob(jobId).orElseThrow(
                () -> new LeaseNotHeldException(jobId, leaseOwner));
        PipelineRun run = repository.lockRun(unlockedJob.runId()).orElseThrow();
        PipelineJob job = repository.lockJob(jobId).orElseThrow();
        if (job.status() != PipelineJobStatus.IN_PROGRESS || !leaseOwner.equals(job.leaseOwner())) {
            throw new LeaseNotHeldException(jobId, leaseOwner);
        }
        return new LeasedJob(run, job);
    }

    private void reclaimExpiredLease(long jobId, Instant now) {
        PipelineJob unlockedJob = repository.findJob(jobId).orElseThrow();
        PipelineRun run = repository.lockRun(unlockedJob.runId()).orElseThrow();
        PipelineJob job = repository.lockJob(jobId).orElseThrow();
        boolean stillExpired = job.status() == PipelineJobStatus.IN_PROGRESS
                && job.leaseExpiresAt() != null && job.leaseExpiresAt().isBefore(now);
        if (stillExpired) {
            recordTransientFailure(new LeasedJob(run, job), LEASE_EXPIRED_REASON, now);
        }
    }

    private void recordTransientFailure(LeasedJob leased, String reason, Instant now) {
        PipelineJob job = leased.job();
        if (leased.run().status() == PipelineRunStatus.CANCELLED) {
            repository.markJobCancelled(job.id(), now);
            return;
        }
        int attempts = job.attempts() + 1;
        if (attempts < job.maxAttempts()) {
            repository.scheduleRetry(job.id(), attempts, now.plus(retryBackoff.delayAfter(attempts)), reason, now);
            return;
        }
        repository.markJobFailed(job.id(), attempts, FailureKind.TRANSIENT,
                GAVE_UP_REASON_FORMAT.formatted(attempts, reason), now);
        failRunIfActive(leased.run(), now);
    }

    private void failRunIfActive(PipelineRun run, Instant now) {
        if (run.status() == PipelineRunStatus.ACTIVE) {
            repository.finishRun(run.id(), PipelineRunStatus.FAILED, now);
        }
    }

    private void applyDependencies(PipelineRun run, PipelineJob finishedJob, Instant now) {
        Map<PipelineStage, StageDependencies.StageJobState> jobStates = repository.jobStatesOfRun(run.id());
        StageDependencies.StageTransition transition =
                StageDependencies.afterStageFinished(finishedJob.stage(), jobStates);
        for (PipelineStage stage : transition.stagesToSkip()) {
            repository.insertJob(run.id(), stage, run.lane(), PipelineJobStatus.SKIPPED, finishedJob.priority(),
                    maxAttempts, now);
        }
        for (PipelineStage stage : transition.stagesToCreate()) {
            repository.insertJob(run.id(), stage, run.lane(), PipelineJobStatus.WAITING, finishedJob.priority(),
                    maxAttempts, now);
        }
        if (transition.readyToSave()) {
            repository.markRunReadyToSave(run.id(), now);
        }
        if (transition.runDone()) {
            repository.finishRun(run.id(), PipelineRunStatus.DONE, now);
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }
}
