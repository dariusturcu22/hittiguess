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
    static final int BURST_LIMIT = 20;
    static final String DUE_WORK_QUERY = """
            SELECT EXISTS (SELECT FROM song_embedding_queue WHERE available_at <= now()
                AND NOT EXISTS (SELECT FROM stage_controls WHERE stage = 'EMBEDDING' AND paused))
            """;
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
            if (!Boolean.TRUE.equals(jdbcTemplate.queryForObject(DUE_WORK_QUERY, Boolean.class))) {
                return;
            }
            // Keeps indexing while songs are being stored, so a playlist does not wait one interval per song.
            for (int indexed = 0; indexed < BURST_LIMIT; indexed++) {
                IndexingOutcome outcome = aiServiceRestClient.post().uri(INDEX_NEXT_PATH).retrieve().body(IndexingOutcome.class);
                if (outcome == null || !outcome.indexed()) {
                    return;
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Catalog embedding indexing postponed: {}", failure.getClass().getSimpleName());
        }
    }

    record IndexingOutcome(boolean indexed) {
    }
}
