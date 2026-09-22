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
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(IMPORT_JOB_EXECUTOR_POOL_SIZE);
        executor.setMaxPoolSize(IMPORT_JOB_EXECUTOR_POOL_SIZE);
        executor.setThreadNamePrefix(IMPORT_JOB_THREAD_NAME_PREFIX);
        executor.initialize();
        return executor;
    }
}
