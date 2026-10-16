package org.dariusturcu.backend.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;

/**
 * Starts stage work on a bounded executor. Each metadata stage gets as many slots as its
 * concurrency limit; a slot runs {@link PipelineStageWorker#drain} and, whenever it leases a job,
 * starts the next free slot of its stage, so parallel work grows only while jobs are due. Slots
 * start on a wake-up from {@link StageWakeUps} or on the poll, which checks every stage with one
 * query. After a poll that found nothing, with no wake-up since, the poll reads the database only
 * once per idle interval so a serverless database can sleep.
 */
@Component
@ConditionalOnProperty(name = "pipeline.stages.enabled", havingValue = "true")
public class PipelineStageDispatcher implements SmartLifecycle, StageWakeUps.Listener {
    public static final long DEFAULT_POLL_INTERVAL_SECONDS = 5;
    public static final long DEFAULT_IDLE_POLL_INTERVAL_SECONDS = 15;
    // Retries further out than this are left to the poll instead of holding a timer each.
    public static final Duration MAXIMUM_SCHEDULED_WAKE_DELAY = Duration.ofMinutes(10);
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineStageDispatcher.class);

    private final PipelineStageWorker worker;
    private final PipelineJobService engine;
    private final StageWakeUps wakeUps;
    private final TaskScheduler scheduler;
    private final Executor executor;
    private final Duration pollInterval;
    private final Duration idlePollInterval;
    private final Clock clock;
    private final Map<PipelineStage, Semaphore> slots = new EnumMap<>(PipelineStage.class);
    private final Map<PipelineStage, Integer> slotLimits = new EnumMap<>(PipelineStage.class);
    private volatile boolean running;
    private volatile boolean lastPollFoundWork = true;
    private volatile Instant lastDatabasePollAt;
    private ScheduledFuture<?> pollTask;

    @Autowired
    public PipelineStageDispatcher(PipelineStageWorker worker, PipelineJobService engine, StageWakeUps wakeUps,
            StageConcurrency concurrency, @Qualifier("pipelineStageScheduler") TaskScheduler scheduler,
            @Qualifier("pipelineStageExecutor") Executor executor,
            @Value("${pipeline.stages.interval-seconds:" + DEFAULT_POLL_INTERVAL_SECONDS + "}") long pollIntervalSeconds,
            @Value("${pipeline.stages.idle-interval-seconds:" + DEFAULT_IDLE_POLL_INTERVAL_SECONDS + "}")
            long idlePollIntervalSeconds) {
        this(worker, engine, wakeUps, concurrency, scheduler, executor, Duration.ofSeconds(pollIntervalSeconds),
                Duration.ofSeconds(idlePollIntervalSeconds), Clock.systemUTC());
    }

    PipelineStageDispatcher(PipelineStageWorker worker, PipelineJobService engine, StageWakeUps wakeUps,
            StageConcurrency concurrency, TaskScheduler scheduler, Executor executor, Duration pollInterval,
            Duration idlePollInterval, Clock clock) {
        if (idlePollInterval.compareTo(pollInterval) < 0) {
            throw new IllegalArgumentException("The idle poll interval cannot be shorter than the poll interval");
        }
        this.worker = worker;
        this.engine = engine;
        this.wakeUps = wakeUps;
        this.scheduler = scheduler;
        this.executor = executor;
        this.pollInterval = pollInterval;
        this.idlePollInterval = idlePollInterval;
        this.clock = clock;
        for (PipelineStage stage : HttpAiStageClient.PATHS.keySet()) {
            slots.put(stage, new Semaphore(concurrency.limit(stage)));
            slotLimits.put(stage, concurrency.limit(stage));
        }
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        wakeUps.register(this);
        pollTask = scheduler.scheduleWithFixedDelay(this::poll, pollInterval);
    }

    @Override
    public synchronized void stop() {
        running = false;
        wakeUps.unregister(this);
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void poll() {
        Instant now = clock.instant();
        boolean idle = !lastPollFoundWork && !anySlotBusy();
        if (idle && lastDatabasePollAt != null && now.isBefore(lastDatabasePollAt.plus(idlePollInterval))) {
            return;
        }
        lastDatabasePollAt = now;
        Set<PipelineStage> dueStages;
        try {
            dueStages = engine.stagesWithDueWork();
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Pipeline poll postponed: {}", unavailable.getClass().getSimpleName());
            return;
        }
        lastPollFoundWork = !dueStages.isEmpty();
        dueStages.forEach(this::startSlot);
    }

    @Override
    public void wake(PipelineStage stage) {
        if (!running) {
            return;
        }
        lastPollFoundWork = true;
        startSlot(stage);
    }

    @Override
    public void wakeAt(PipelineStage stage, Instant dueAt) {
        if (!running) {
            return;
        }
        Duration delay = Duration.between(clock.instant(), dueAt);
        if (delay.isNegative() || delay.isZero()) {
            wake(stage);
        } else if (delay.compareTo(MAXIMUM_SCHEDULED_WAKE_DELAY) <= 0) {
            try {
                scheduler.schedule(() -> wake(stage), dueAt);
            } catch (RejectedExecutionException shuttingDown) {
                LOGGER.debug("Wake-up for {} dropped while shutting down", stage);
            }
        }
    }

    int busySlots(PipelineStage stage) {
        Semaphore stageSlots = slots.get(stage);
        return stageSlots == null ? 0 : slotLimits.get(stage) - stageSlots.availablePermits();
    }

    private boolean anySlotBusy() {
        return slots.keySet().stream().anyMatch(stage -> busySlots(stage) > 0);
    }

    private void startSlot(PipelineStage stage) {
        Semaphore stageSlots = slots.get(stage);
        if (stageSlots == null || !running || !stageSlots.tryAcquire()) {
            return;
        }
        try {
            executor.execute(() -> runSlot(stage, stageSlots));
        } catch (RejectedExecutionException full) {
            stageSlots.release();
            LOGGER.warn("Pipeline stage {} slot not started, the executor is full", stage);
        }
    }

    private void runSlot(PipelineStage stage, Semaphore stageSlots) {
        try {
            if (worker.drain(stage, () -> startSlot(stage)) > 0) {
                lastPollFoundWork = true;
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Pipeline stage {} slot stopped: {}", stage, failure.getClass().getSimpleName());
        } finally {
            stageSlots.release();
        }
    }
}
