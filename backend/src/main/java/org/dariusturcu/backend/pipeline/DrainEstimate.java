package org.dariusturcu.backend.pipeline;

import java.time.Duration;
import java.util.List;

/** Drain estimates use measured completions or duration and concurrency, capped by source pace. */
public final class DrainEstimate {
    public static final Duration DURATION_WINDOW = Duration.ofMinutes(60);
    public static final Duration THROUGHPUT_WINDOW = Duration.ofMinutes(10);
    public static final int MINIMUM_MEASURED_JOBS = 2;
    private static final double MILLISECONDS_PER_MINUTE = Duration.ofMinutes(1).toMillis();
    private static final double SECONDS_PER_MINUTE = Duration.ofMinutes(1).toSeconds();
    private static final long NOTHING_LEFT = 0;

    public record Inputs(long remainingJobs, int concurrencyLimit, Long averageDurationMs,
                         long finishedInThroughputWindow, Integer paceIntervalMs, boolean paused,
                         Duration cooldownRemaining) {
    }

    private DrainEstimate() {
    }

    /** Jobs finished per minute over the throughput window. */
    public static double measuredJobsPerMinute(long finishedInThroughputWindow) {
        return finishedInThroughputWindow / (double) THROUGHPUT_WINDOW.toMinutes();
    }

    /** The effective jobs per minute the estimate uses, or null without a basis. */
    public static Double effectiveJobsPerMinute(Inputs inputs) {
        Double measured = inputs.finishedInThroughputWindow() >= MINIMUM_MEASURED_JOBS
                ? measuredJobsPerMinute(inputs.finishedInThroughputWindow()) : null;
        Double modelled = inputs.averageDurationMs() != null && inputs.averageDurationMs() > 0
                ? inputs.concurrencyLimit() * MILLISECONDS_PER_MINUTE / inputs.averageDurationMs() : null;
        Double throughput = measured;
        if (throughput == null || (modelled != null && modelled > throughput)) {
            throughput = modelled;
        }
        if (throughput == null || throughput <= 0) {
            return null;
        }
        if (inputs.paceIntervalMs() != null && inputs.paceIntervalMs() > 0) {
            throughput = Math.min(throughput, MILLISECONDS_PER_MINUTE / inputs.paceIntervalMs());
        }
        return throughput;
    }

    public static Long seconds(Inputs inputs) {
        if (inputs.remainingJobs() <= 0) {
            return NOTHING_LEFT;
        }
        if (inputs.paused()) {
            return null;
        }
        Double jobsPerMinute = effectiveJobsPerMinute(inputs);
        if (jobsPerMinute == null) {
            return null;
        }
        long processingSeconds = (long) Math.ceil(inputs.remainingJobs() / jobsPerMinute * SECONDS_PER_MINUTE);
        Duration cooldown = inputs.cooldownRemaining();
        long cooldownSeconds = cooldown == null || cooldown.isNegative() ? NOTHING_LEFT : ceilingSeconds(cooldown);
        return processingSeconds + cooldownSeconds;
    }

    /**
     * The whole pipeline's estimate: the slowest stage, since stages work in parallel. Unknown when
     * any stage that still holds jobs has an unknown estimate.
     */
    public static Long overall(List<StageEstimate> stages) {
        long slowest = NOTHING_LEFT;
        for (StageEstimate stage : stages) {
            if (stage.remainingJobs() <= 0) {
                continue;
            }
            if (stage.seconds() == null) {
                return null;
            }
            slowest = Math.max(slowest, stage.seconds());
        }
        return slowest;
    }

    public record StageEstimate(long remainingJobs, Long seconds) {
    }

    private static long ceilingSeconds(Duration duration) {
        return duration.toSeconds() + (duration.toNanosPart() > 0 ? 1 : 0);
    }
}
