package org.dariusturcu.backend.pipeline;

/**
 * When a job's stored failure fields describe its current state. They do only for a failed job
 * and for a waiting job that has failed at least once and will retry. A finished, skipped, or
 * cancelled job, or one waiting only on a pace deferral, reports no failure, even when rows
 * written before completion cleared those fields still hold them.
 */
public final class PipelineJobFailures {
    private static final String VISIBLE_CONDITION = "(%1$s.status = 'FAILED' OR (%1$s.status = 'WAITING' AND %1$s.attempts > 0))";

    private PipelineJobFailures() {
    }

    public static boolean visible(PipelineJobStatus status, int failedTries) {
        return status == PipelineJobStatus.FAILED || (status == PipelineJobStatus.WAITING && failedTries > 0);
    }

    public static String visibleReason(PipelineJob job) {
        return visible(job.status(), job.attempts()) ? job.failureReason() : null;
    }

    /** The same rule as {@link #visible} as a SQL condition over the pipeline_jobs row named {@code alias}. */
    public static String visibleCondition(String alias) {
        return VISIBLE_CONDITION.formatted(alias);
    }
}
