package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.observability.CorrelationIdPropagatingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class PipelineStageConfig {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(4);
    static final int SCHEDULER_THREADS = 2;
    static final int EXECUTOR_HEADROOM_THREADS = 2;

    @Bean(defaultCandidate = false)
    public RestClient pipelineStageRestClient(@Value("${ai.service.base-url}") String baseUrl,
            @Value("${ai.service.internal-api-key}") String internalApiKey,
            CorrelationIdPropagatingInterceptor correlation) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(CONNECT_TIMEOUT);
        requests.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestInterceptor(correlation).requestFactory(requests).build();
    }

    // Only polls and wake-up timers run here; stage calls run on the executor.
    @Bean
    public ThreadPoolTaskScheduler pipelineStageScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_THREADS);
        scheduler.setThreadNamePrefix("pipeline-poll-");
        return scheduler;
    }

    @Bean
    public ThreadPoolTaskExecutor pipelineStageExecutor(StageConcurrency concurrency) {
        return stageExecutor(concurrency);
    }

    /** Spare threads accept handoffs before the previous slot thread returns. */
    public static ThreadPoolTaskExecutor stageExecutor(StageConcurrency concurrency) {
        int threads = concurrency.totalMetadataLimit() + EXECUTOR_HEADROOM_THREADS;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(threads);
        executor.setThreadNamePrefix("pipeline-stage-");
        executor.initialize();
        return executor;
    }
}
