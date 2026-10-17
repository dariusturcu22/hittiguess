package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.admin.AdminDashboardDTO;
import org.dariusturcu.backend.model.song.AdminCatalogCountsDTO;
import org.dariusturcu.backend.model.usage.AdminUsageDTO;
import org.dariusturcu.backend.repository.AdminSongCatalogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardServiceTest {

    private static final long ALL_COUNT = 50;
    private static final long VERIFIED_COUNT = 30;
    private static final long NEEDS_REVIEW_COUNT = 12;
    private static final long IN_QUEUE_COUNT = 5;
    private static final long PROCESSING_COUNT = 2;
    private static final long ERROR_COUNT = 1;
    private static final long WAITING_ROWS = 6;
    private static final long IN_PROGRESS_ROWS = 2;

    private final AdminSongCatalogService adminSongCatalogService = mock(AdminSongCatalogService.class);
    private final AdminSongCatalogRepository catalogRepository = mock(AdminSongCatalogRepository.class);
    private final ApiUsageService apiUsageService = mock(ApiUsageService.class);
    private final AdminDashboardService service =
            new AdminDashboardService(adminSongCatalogService, catalogRepository, apiUsageService);

    @Test
    void theDashboardCombinesCatalogCountsQueueTotalsAndTheUsageView() {
        AdminUsageDTO usage = new AdminUsageDTO(null, null, null, BigDecimal.ONE, BigDecimal.TEN,
                LocalDate.of(2026, 10, 16));
        when(adminSongCatalogService.catalogCounts()).thenReturn(new AdminCatalogCountsDTO(
                ALL_COUNT, VERIFIED_COUNT, NEEDS_REVIEW_COUNT, IN_QUEUE_COUNT, PROCESSING_COUNT, ERROR_COUNT));
        when(catalogRepository.countQueue())
                .thenReturn(new AdminSongCatalogRepository.QueueCounts(WAITING_ROWS, IN_PROGRESS_ROWS));
        when(apiUsageService.usage()).thenReturn(usage);

        AdminDashboardDTO dashboard = service.dashboard();

        assertThat(dashboard.songs().verified()).isEqualTo(VERIFIED_COUNT);
        assertThat(dashboard.songs().needsReview()).isEqualTo(NEEDS_REVIEW_COUNT);
        assertThat(dashboard.songs().inQueue()).isEqualTo(IN_QUEUE_COUNT);
        assertThat(dashboard.songs().processing()).isEqualTo(PROCESSING_COUNT);
        assertThat(dashboard.songs().error()).isEqualTo(ERROR_COUNT);
        assertThat(dashboard.songs().total()).isEqualTo(ALL_COUNT);
        assertThat(dashboard.queue().waiting()).isEqualTo(WAITING_ROWS);
        assertThat(dashboard.queue().inProgress()).isEqualTo(IN_PROGRESS_ROWS);
        assertThat(dashboard.usage()).isSameAs(usage);
    }

    @Test
    void theDashboardIncludesQueueControlsAndReserveDataWithoutReplacingExistingFields() {
        var pipeline = mock(org.dariusturcu.backend.pipeline.AdminPipelineService.class);
        var queues = new org.dariusturcu.backend.model.admin.QueueOverviewDTO(java.util.List.of(), true,
                WAITING_ROWS, org.dariusturcu.backend.pipeline.PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS, true, null);
        when(pipeline.queues()).thenReturn(queues);
        when(adminSongCatalogService.catalogCounts()).thenReturn(new AdminCatalogCountsDTO(
                ALL_COUNT, VERIFIED_COUNT, NEEDS_REVIEW_COUNT, IN_QUEUE_COUNT, PROCESSING_COUNT, ERROR_COUNT));
        when(catalogRepository.countQueue()).thenReturn(new AdminSongCatalogRepository.QueueCounts(WAITING_ROWS, IN_PROGRESS_ROWS));
        var dashboard = new AdminDashboardService(adminSongCatalogService, catalogRepository, apiUsageService, pipeline).dashboard();
        assertThat(dashboard.pipeline()).isSameAs(queues);
        assertThat(dashboard.songs().total()).isEqualTo(ALL_COUNT);
        assertThat(dashboard.queue().waiting()).isEqualTo(WAITING_ROWS);
    }
}
