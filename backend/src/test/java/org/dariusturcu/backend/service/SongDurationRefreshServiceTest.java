package org.dariusturcu.backend.service;

import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.assertThat;

class SongDurationRefreshServiceTest {
    private static final String ENDPOINT = "/metadata/video-durations";
    private static final int OFFICIAL_DURATION_SECONDS = 210;
    private static final int VIDEO_BATCH_SIZE = 50;
    private static final int EXPECTED_BATCH_SELECTIONS = 3;
    private static final String SIX_HOUR_INTERVAL = "PT6H";
    private SongRepository repository;
    private SongDurationRefreshService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        repository = mock(SongRepository.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SongDurationRefreshService(repository, builder.build());
    }

    @Test
    void refreshesSuccessiveBatchesAndRecordsUnavailableVideos() {
        when(repository.findDurationRefreshCandidates(any(), any()))
                .thenReturn(List.of("known", "deleted"), List.of("next"), List.of());
        server.expect(requestTo(ENDPOINT)).andExpect(content().json("{\"video_ids\":[\"known\",\"deleted\"]}"))
                .andRespond(withSuccess("{\"durations\":{\"known\":210,\"deleted\":null}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT)).andExpect(content().json("{\"video_ids\":[\"next\"]}"))
                .andRespond(withSuccess("{\"durations\":{\"next\":210}}", MediaType.APPLICATION_JSON));
        service.refreshExpiredDurations();
        verify(repository).updateRefreshedDuration(eq("known"), eq(OFFICIAL_DURATION_SECONDS), any(Instant.class), any(Instant.class));
        verify(repository).updateRefreshedDuration(eq("deleted"), isNull(), any(Instant.class), any(Instant.class));
        verify(repository).updateRefreshedDuration(eq("next"), eq(OFFICIAL_DURATION_SECONDS), any(Instant.class), any(Instant.class));
        verify(repository).clearExpiredDurations(any());
        verify(repository, times(EXPECTED_BATCH_SELECTIONS)).findDurationRefreshCandidates(any(), argThat(page -> page.getPageSize() == VIDEO_BATCH_SIZE));
        server.verify();
    }

    @Test
    void failureStopsFurtherRequestsAndClearsStaleValuesWithoutMarkingThemFresh() {
        when(repository.findDurationRefreshCandidates(any(), any())).thenReturn(List.of("known"));
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());
        service.refreshExpiredDurations();
        verify(repository, never()).updateRefreshedDuration(anyString(), any(), any(), any());
        verify(repository).clearExpiredDurations(any());
        verify(repository).findDurationRefreshCandidates(any(), any());
        server.verify();
    }

    @Test
    void incompleteResponseDoesNotExtendFreshness() {
        when(repository.findDurationRefreshCandidates(any(), any())).thenReturn(List.of("known"));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{\"durations\":{}}", MediaType.APPLICATION_JSON));
        service.refreshExpiredDurations();
        verify(repository, never()).updateRefreshedDuration(anyString(), any(), any(), any());
        verify(repository).clearExpiredDurations(any());
        server.verify();
    }

    @Test
    void freshCatalogMakesNoExternalRequests() {
        when(repository.findDurationRefreshCandidates(any(), any())).thenReturn(List.of());
        service.refreshExpiredDurations();
        verify(repository, never()).updateRefreshedDuration(anyString(), any(), any(), any());
        server.verify();
    }

    @Test
    void scheduledRefreshUsesTheRequestedSixHourInterval() throws NoSuchMethodException {
        Scheduled schedule = SongDurationRefreshService.class.getMethod("refreshExpiredDurations").getAnnotation(Scheduled.class);
        assertThat(schedule.fixedDelayString()).isEqualTo(SIX_HOUR_INTERVAL);
    }
}
