package org.dariusturcu.backend.pipeline;

/** Failure details are visible only on failed jobs and waiting retries. */
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
