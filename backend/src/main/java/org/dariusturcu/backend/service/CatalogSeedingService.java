package org.dariusturcu.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.BacklogStatusDTO;
import org.dariusturcu.backend.model.song.BacklogQueueItemDTO;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PatientRecheckDTO;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.util.SongArtistFormatter;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private static final Set<PendingImportOrigin> RECHECK_ORIGINS =
            Set.of(PendingImportOrigin.FAST_TIER_RECHECK, PendingImportOrigin.USER_ADD_RECHECK);

    private final PendingImportRepository pendingImportRepository;
    private final YoutubeIdLookupService youtubeIdLookupService;
    private final PendingImportProcessor pendingImportProcessor;
    private final MetadataPriorityCoordinator metadataPriorityCoordinator;
    private final PlaylistExpansionService playlistExpansionService;
    private final SongRepository songRepository;
    private final TaskExecutor backlogDrainExecutor;
    // Only one drain works the backlog at a time, whether the daily sweep or an admin
    // started it, so no row is picked up twice.
    private final AtomicBoolean isDraining = new AtomicBoolean(false);

    private final long dailyDrainQuota;

    public CatalogSeedingService(
            PendingImportRepository pendingImportRepository,
            YoutubeIdLookupService youtubeIdLookupService,
            PendingImportProcessor pendingImportProcessor,
            MetadataPriorityCoordinator metadataPriorityCoordinator,
            PlaylistExpansionService playlistExpansionService,
            SongRepository songRepository,
            @Qualifier("backlogDrainExecutor") TaskExecutor backlogDrainExecutor,
            @Value("${catalog.seeding.daily-drain-quota}") long dailyDrainQuota) {
        this.pendingImportRepository = pendingImportRepository;
        this.youtubeIdLookupService = youtubeIdLookupService;
        this.pendingImportProcessor = pendingImportProcessor;
        this.metadataPriorityCoordinator = metadataPriorityCoordinator;
        this.playlistExpansionService = playlistExpansionService;
        this.songRepository = songRepository;
        this.backlogDrainExecutor = backlogDrainExecutor;
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
     * Queues a song a user path already saved with a provisional answer (a fast-tier
     * import, a manual add) so the patient pipeline rechecks it at low priority. This
     * deliberately bypasses the already-known filter that enqueue applies: the point is
     * to reprocess a song that is already in the catalog. The provisional year is kept
     * so the admin can see whether the patient tier changed it.
     */
    @Transactional
    public PendingImport enqueuePatientRecheck(String youtubeId, PendingImportOrigin origin, Integer provisionalYear) {
        PendingImport pendingImport = new PendingImport();
        pendingImport.setYoutubeId(youtubeId);
        pendingImport.setStatus(PendingImportStatus.PENDING);
        pendingImport.setEnqueuedAt(Instant.now());
        pendingImport.setOrigin(origin);
        pendingImport.setProvisionalYear(provisionalYear);
        return pendingImportRepository.save(pendingImport);
    }

    @Transactional(readOnly = true)
    public BacklogStatusDTO backlogStatus() {
        long pendingCount = pendingImportRepository.countByStatus(PendingImportStatus.PENDING);
        long processedTodayCount = processedTodayCount();
        long quotaRemaining = Math.max(0, dailyDrainQuota - processedTodayCount);
        List<BacklogQueueItemDTO> queueItems = pendingImportRepository.findTop7ByOrderByEnqueuedAtDesc().stream()
                .map(pendingImport -> new BacklogQueueItemDTO(
                        pendingImport.getYoutubeId(),
                        pendingImport.getStatus(),
                        pendingImport.getFailureReason()))
                .toList();
        return new BacklogStatusDTO(pendingCount, processedTodayCount, dailyDrainQuota, quotaRemaining, queueItems,
                recentRechecks(), isDraining.get());
    }

    // The newest rechecks of provisional answers, with the song as it stands now, so the
    // admin can see each provisional year beside the patient tier's.
    private List<PatientRecheckDTO> recentRechecks() {
        List<PendingImport> rechecks = pendingImportRepository.findTop50ByOriginInOrderByEnqueuedAtDesc(RECHECK_ORIGINS);
        Map<String, Song> songsByYoutubeId = songRepository.findByYoutubeIdIn(
                        rechecks.stream().map(PendingImport::getYoutubeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Song::getYoutubeId, Function.identity(), (firstSong, laterSong) -> firstSong));
        return rechecks.stream()
                .map(recheck -> {
                    Song song = songsByYoutubeId.get(recheck.getYoutubeId());
                    return new PatientRecheckDTO(
                            recheck.getYoutubeId(),
                            recheck.getOrigin(),
                            recheck.getStatus(),
                            song == null ? null : song.getTitle(),
                            song == null ? null : SongArtistFormatter.formatCredit(song),
                            recheck.getProvisionalYear(),
                            recheck.getPatientYear(),
                            recheck.getEnqueuedAt(),
                            recheck.getProcessedAt());
                })
                .toList();
    }

    /**
     * Resolves pending imports up to the remaining daily quota, one at a time, stopping
     * before the next item whenever on-the-spot traffic holds the shared external
     * rate-limit budget. Returns how many items it resolved this run.
     */
    public int drainBacklog() {
        if (!isDraining.compareAndSet(false, true)) {
            return 0;
        }
        try {
            return drainWithinQuota();
        } finally {
            isDraining.set(false);
        }
    }

    // The admin's run-now action: starts a drain in the background instead of waiting
    // for the daily sweep. Returns false when a drain is already running.
    public boolean startDrainNow() {
        if (isDraining.get()) {
            return false;
        }
        backlogDrainExecutor.execute(this::drainBacklog);
        return true;
    }

    public boolean isDraining() {
        return isDraining.get();
    }

    private int drainWithinQuota() {
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
