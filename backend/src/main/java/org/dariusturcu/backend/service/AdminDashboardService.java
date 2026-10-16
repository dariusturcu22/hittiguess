package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.admin.AdminDashboardDTO;
import org.dariusturcu.backend.model.admin.DashboardQueueDTO;
import org.dariusturcu.backend.model.admin.DashboardSongCountsDTO;
import org.dariusturcu.backend.model.song.AdminCatalogCountsDTO;
import org.dariusturcu.backend.repository.AdminSongCatalogRepository;
import org.springframework.stereotype.Service;

/**
 * Assembles the admin dashboard from the catalog counts, the queue totals, and the usage view.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminSongCatalogService adminSongCatalogService;
    private final AdminSongCatalogRepository catalogRepository;
    private final ApiUsageService apiUsageService;

    public AdminDashboardDTO dashboard() {
        AdminCatalogCountsDTO catalogCounts = adminSongCatalogService.catalogCounts();
        AdminSongCatalogRepository.QueueCounts queueCounts = catalogRepository.countQueue();
        return new AdminDashboardDTO(
                new DashboardSongCountsDTO(
                        catalogCounts.verified(),
                        catalogCounts.needsReview(),
                        catalogCounts.inQueue(),
                        catalogCounts.processing(),
                        catalogCounts.error(),
                        catalogCounts.all()),
                new DashboardQueueDTO(queueCounts.waiting(), queueCounts.inProgress()),
                apiUsageService.usage());
    }
}
