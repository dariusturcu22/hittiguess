package org.dariusturcu.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.BacklogStatusDTO;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The admin catalog-seeding backlog: enqueue submitted YouTube IDs the catalog does
 * not already know, report backlog status, and drain pending imports on the daily
 * quota. The drain yields the shared external rate-limit budget to on-the-spot
 * traffic through the priority coordinator, and re-runs no already-known song
 * because enqueue filters those out first.
 */
@Slf4j
@Service
public class CatalogSeedingService {

    private static final Set<PendingImportStatus> ACTIVE_BACKLOG_STATUSES =
            Set.of(PendingImportStatus.PENDING, PendingImportStatus.PROCESSING);

    private final PendingImportRepository pendingImportRepository;
    private final YoutubeIdLookupService youtubeIdLookupService;
    private final PendingImportProcessor pendingImportProcessor;
    private final MetadataPriorityCoordinator metadataPriorityCoordinator;
    private final PlaylistExpansionService playlistExpansionService;

    private final long dailyDrainQuota;

    public CatalogSeedingService(
            PendingImportRepository pendingImportRepository,
            YoutubeIdLookupService youtubeIdLookupService,
            PendingImportProcessor pendingImportProcessor,
            MetadataPriorityCoordinator metadataPriorityCoordinator,
            PlaylistExpansionService playlistExpansionService,
            @Value("${catalog.seeding.daily-drain-quota}") long dailyDrainQuota) {
        this.pendingImportRepository = pendingImportRepository;
        this.youtubeIdLookupService = youtubeIdLookupService;
        this.pendingImportProcessor = pendingImportProcessor;
        this.metadataPriorityCoordinator = metadataPriorityCoordinator;
        this.playlistExpansionService = playlistExpansionService;
        this.dailyDrainQuota = dailyDrainQuota;
    }

    /**
     * Expands the request's playlist link, when present, merges it with its own
     * submitted video IDs or links, and enqueues the result the same way the plain
     * ID-collection overload does.
     */
    @Transactional
    public EnqueueResultDTO enqueue(AdminCatalogSeedingRequest request) {
        List<String> parsedYoutubeIds = YoutubeLinkParser.parseAllVideoIds(request.youtubeIds());
        List<String> mergedYoutubeIds = playlistExpansionService.expandAndMerge(request.playlistLink(), parsedYoutubeIds);
        return enqueue(mergedYoutubeIds);
    }

    @Transactional
    public EnqueueResultDTO enqueue(Collection<String> submittedYoutubeIds) {
        YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(submittedYoutubeIds);
        Set<String> alreadyKnownIds = lookupResult.knownYoutubeIds();
        Set<String> candidateIds = lookupResult.unknownYoutubeIds();

        Set<String> alreadyQueuedIds = new LinkedHashSet<>(
                pendingImportRepository.findYoutubeIdsAlreadyQueued(candidateIds, ACTIVE_BACKLOG_STATUSES));

        Set<String> idsToEnqueue = new LinkedHashSet<>(candidateIds);
        idsToEnqueue.removeAll(alreadyQueuedIds);

        for (String youtubeId : idsToEnqueue) {
            PendingImport pendingImport = new PendingImport();
            pendingImport.setYoutubeId(youtubeId);
            pendingImport.setStatus(PendingImportStatus.PENDING);
            pendingImport.setEnqueuedAt(Instant.now());
            pendingImportRepository.save(pendingImport);
        }

        return new EnqueueResultDTO(idsToEnqueue, alreadyKnownIds, alreadyQueuedIds);
    }

    /**
     * Re-enqueues a song the on-the-spot fast tier already resolved provisionally, so
     * the patient pipeline reprocesses it at low priority. This deliberately bypasses
     * the already-known filter that enqueue applies: the point is to reprocess a song
     * the fast tier answered, not to skip it as already resolved.
     */
    @Transactional
    public PendingImport reEnqueueForPatientReprocessing(String youtubeId) {
        PendingImport pendingImport = new PendingImport();
        pendingImport.setYoutubeId(youtubeId);
        pendingImport.setStatus(PendingImportStatus.PENDING);
        pendingImport.setEnqueuedAt(Instant.now());
        return pendingImportRepository.save(pendingImport);
    }

    @Transactional(readOnly = true)
    public BacklogStatusDTO backlogStatus() {
        long pendingCount = pendingImportRepository.countByStatus(PendingImportStatus.PENDING);
        long processedTodayCount = processedTodayCount();
        long quotaRemaining = Math.max(0, dailyDrainQuota - processedTodayCount);
        return new BacklogStatusDTO(pendingCount, processedTodayCount, dailyDrainQuota, quotaRemaining);
    }

    /**
     * Resolves pending imports up to the remaining daily quota, one at a time, stopping
     * before the next item whenever on-the-spot traffic holds the shared external
     * rate-limit budget. Returns how many items it resolved this run.
     */
    public int drainBacklog() {
        long remainingQuota = dailyDrainQuota - processedTodayCount();
        if (remainingQuota <= 0) {
            return 0;
        }

        Limit drainLimit = Limit.of((int) Math.min(remainingQuota, Integer.MAX_VALUE));
        List<PendingImport> batch = pendingImportRepository.findByStatusOrderByEnqueuedAtAsc(
                PendingImportStatus.PENDING, drainLimit);

        int resolvedCount = 0;
        for (PendingImport pendingImport : batch) {
            if (!metadataPriorityCoordinator.mayDrainProceed()) {
                break;
            }
            pendingImportProcessor.process(pendingImport.getId());
            resolvedCount++;
        }
        return resolvedCount;
    }

    private long processedTodayCount() {
        return pendingImportRepository.countByStatusAndProcessedAtAfter(
                PendingImportStatus.DONE, Instant.now().truncatedTo(ChronoUnit.DAYS));
    }
}
