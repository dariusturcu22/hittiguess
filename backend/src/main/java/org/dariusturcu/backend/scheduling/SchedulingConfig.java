package org.dariusturcu.backend.scheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// A pooled TaskScheduler bean, used two ways: directly by GameSessionScheduler for the
// round-level countdown/betting/turn-timeout/abandon timers, and implicitly by every
// @Scheduled method in the application (including GroupExpirySweeper's sweep), since
// Spring's scheduling infrastructure picks up a TaskScheduler bean if one exists instead
// of falling back to its single-threaded default.
@Configuration
public class SchedulingConfig {

    private static final int SCHEDULER_POOL_SIZE = 4;
    private static final String THREAD_NAME_PREFIX = "game-session-scheduler-";
    private static final int IMPORT_JOB_EXECUTOR_POOL_SIZE = 2;
    private static final String IMPORT_JOB_THREAD_NAME_PREFIX = "playlist-import-";
    // Songs in each fast-tier stage at once, across every running import. The identify
    // stage is one LLM call per song; the year stage queues behind the AI service's
    // per-source pacers, so more workers than this would only wait there.
    private static final int FAST_TIER_IDENTIFY_CONCURRENCY = 8;
    private static final int FAST_TIER_DATING_CONCURRENCY = 8;
    private static final String FAST_TIER_IDENTIFY_THREAD_NAME_PREFIX = "fast-tier-identify-";
    private static final String FAST_TIER_DATING_THREAD_NAME_PREFIX = "fast-tier-dating-";
    private static final int BACKLOG_DRAIN_POOL_SIZE = 1;
    private static final String BACKLOG_DRAIN_THREAD_NAME_PREFIX = "backlog-drain-";

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        scheduler.initialize();
        return scheduler;
    }

    // Imports resolve slowly against YouTube and must never borrow the game timer pool.
    @Bean
    public TaskExecutor importJobExecutor() {
        return fixedPool(IMPORT_JOB_EXECUTOR_POOL_SIZE, IMPORT_JOB_THREAD_NAME_PREFIX);
    }

    @Bean
    public TaskExecutor fastTierIdentifyExecutor() {
        return fixedPool(FAST_TIER_IDENTIFY_CONCURRENCY, FAST_TIER_IDENTIFY_THREAD_NAME_PREFIX);
    }

    @Bean
    public TaskExecutor fastTierDatingExecutor() {
        return fixedPool(FAST_TIER_DATING_CONCURRENCY, FAST_TIER_DATING_THREAD_NAME_PREFIX);
    }

    // Runs an admin-requested backlog drain off the request thread; the drain is long
    // and strictly one item at a time, so it gets its own single worker.
    @Bean
    public TaskExecutor backlogDrainExecutor() {
        return fixedPool(BACKLOG_DRAIN_POOL_SIZE, BACKLOG_DRAIN_THREAD_NAME_PREFIX);
    }

    private static ThreadPoolTaskExecutor fixedPool(int poolSize, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
