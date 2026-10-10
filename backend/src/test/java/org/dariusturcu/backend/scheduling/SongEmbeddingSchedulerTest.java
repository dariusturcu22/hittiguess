package org.dariusturcu.backend.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SongEmbeddingSchedulerTest {
    private JdbcTemplate database;
    private MockRestServiceServer server;
    private SongEmbeddingScheduler scheduler;

    @BeforeEach
    void setUp() {
        database = mock(JdbcTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        scheduler = new SongEmbeddingScheduler(database, builder.build());
    }

    @Test
    void dueWorkCallsInternalIndexer() {
        when(database.queryForObject(SongEmbeddingScheduler.DUE_WORK_QUERY, Boolean.class)).thenReturn(true);
        server.expect(requestTo(SongEmbeddingScheduler.INDEX_NEXT_PATH)).andRespond(withSuccess());
        scheduler.indexNext();
        server.verify();
    }

    @Test
    void emptyQueueDoesNotWakeAiService() {
        when(database.queryForObject(SongEmbeddingScheduler.DUE_WORK_QUERY, Boolean.class)).thenReturn(false);
        scheduler.indexNext();
        server.verify();
    }

    @Test
    void unavailableAiServiceLeavesDurableQueueUntouched() {
        when(database.queryForObject(SongEmbeddingScheduler.DUE_WORK_QUERY, Boolean.class)).thenReturn(true);
        server.expect(requestTo(SongEmbeddingScheduler.INDEX_NEXT_PATH)).andRespond(withServerError());
        scheduler.indexNext();
        verify(database).queryForObject(SongEmbeddingScheduler.DUE_WORK_QUERY, Boolean.class);
        verifyNoMoreInteractions(database);
        server.verify();
    }
}
