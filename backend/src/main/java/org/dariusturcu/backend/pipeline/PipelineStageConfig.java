package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.observability.CorrelationIdPropagatingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pipeline.stages.enabled", havingValue = "true")
public class PipelineStageConfig {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(4);

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

    @Bean
    public ThreadPoolTaskScheduler pipelineStageScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(HttpAiStageClient.PATHS.size());
        scheduler.setThreadNamePrefix("pipeline-stage-");
        return scheduler;
    }
}
