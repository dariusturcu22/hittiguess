package org.dariusturcu.backend.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SongEmbeddingScheduler {
    static final String INDEX_NEXT_PATH = "/catalog/index-next";
    static final String DUE_WORK_QUERY = "SELECT EXISTS (SELECT FROM song_embedding_queue WHERE available_at <= now())";
    private final JdbcTemplate jdbcTemplate;
    private final RestClient aiServiceRestClient;

    public SongEmbeddingScheduler(@Qualifier("coreJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("catalogIndexingRestClient") RestClient aiServiceRestClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiServiceRestClient = aiServiceRestClient;
    }

    @Scheduled(fixedDelayString = "${catalog.embedding.interval-seconds:10}", timeUnit = TimeUnit.SECONDS,
            scheduler = "catalogEmbeddingTaskScheduler")
    public void indexNext() {
        try {
            if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(DUE_WORK_QUERY, Boolean.class))) {
                aiServiceRestClient.post().uri(INDEX_NEXT_PATH).retrieve().toBodilessEntity();
            }
        } catch (RuntimeException failure) {
            log.warn("Catalog embedding indexing postponed: {}", failure.getClass().getSimpleName());
        }
    }
}
