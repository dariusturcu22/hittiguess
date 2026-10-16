package org.dariusturcu.backend.pipeline;

/**
 * Current job counts of one stage. Waiting covers jobs of active runs that are queued, including
 * retries still in backoff and jobs whose lease expired without a report (they return to the
 * queue on the next lease); waitingDue is the part of it that may be leased now. In progress
 * covers held, unexpired leases, including a cancelled run's job that is still finishing.
 * Retrying is the part of waiting that has failed at least once. Failed covers failed jobs of
 * runs that are still failed, so discarded or retried runs no longer count. Done today covers
 * jobs that finished done since the start of the UTC day; skipped and cancelled jobs never count.
 * Stages a run has not reached yet have no job, so they appear in no count.
 */
public record StageCounts(
        PipelineStage stage,
        boolean paused,
        long waiting,
        long inProgress,
        long doneToday,
        long failed,
        long retrying,
        long waitingDue) {
}
