package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.dariusturcu.backend.pipeline.ImportBatchKind;
import org.dariusturcu.backend.pipeline.PipelineJobService;
import org.dariusturcu.backend.pipeline.PipelineLane;
import org.dariusturcu.backend.pipeline.PipelineRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CatalogSeedingService {

    private static final String ENQUEUE_LOCK_KEY = "pending-import-enqueue";
    private final PendingImportRepository pendingImportRepository;
    private final YoutubeIdLookupService youtubeIdLookupService;
    private final PlaylistExpansionService playlistExpansionService;
    private final SongRepository songRepository;
    private final PipelineJobService pipeline;

    public CatalogSeedingService(PendingImportRepository pendingImportRepository,
            YoutubeIdLookupService youtubeIdLookupService, PlaylistExpansionService playlistExpansionService,
            SongRepository songRepository, PipelineJobService pipeline) {
        this.pendingImportRepository = pendingImportRepository;
        this.youtubeIdLookupService = youtubeIdLookupService;
        this.playlistExpansionService = playlistExpansionService;
        this.songRepository = songRepository;
        this.pipeline = pipeline;
    }

    @Transactional
    public EnqueueResultDTO enqueue(AdminCatalogSeedingRequest request) {
        List<String> parsedYoutubeIds = YoutubeLinkParser.parseAllVideoIds(request.youtubeIds());
        List<String> mergedYoutubeIds = playlistExpansionService.expandAndMerge(request.playlistLink(), parsedYoutubeIds);
        return enqueueSubmitted(mergedYoutubeIds, request.playlistLink());
    }

    @Transactional
    public EnqueueResultDTO enqueue(Collection<String> submittedYoutubeIds) {
        return enqueueSubmitted(submittedYoutubeIds, null);
    }

    private EnqueueResultDTO enqueueSubmitted(Collection<String> submittedYoutubeIds, String playlistLink) {
        // Overlapping submissions would otherwise each see nothing queued and each queue every ID.
        songRepository.acquireTransactionLock(ENQUEUE_LOCK_KEY);
        YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(submittedYoutubeIds);
        Set<String> alreadyKnownIds = lookupResult.knownYoutubeIds();
        Set<String> candidateIds = lookupResult.unknownYoutubeIds();

        return enqueueRuns(candidateIds, alreadyKnownIds, playlistLink);
    }

    /** Preserves the provisional answer in history while stage queues recheck the saved song. */
    @Transactional
    public PendingImport enqueuePatientRecheck(String youtubeId, PendingImportOrigin origin, Integer provisionalYear) {
        pipeline.startRun(youtubeId, PipelineLane.USER_IMPORT, origin, null, provisionalYear);
        PendingImport pendingImport = new PendingImport();
        pendingImport.setYoutubeId(youtubeId);
        pendingImport.setStatus(PendingImportStatus.PENDING);
        pendingImport.setEnqueuedAt(Instant.now());
        pendingImport.setOrigin(origin);
        pendingImport.setProvisionalYear(provisionalYear);
        return pendingImportRepository.save(pendingImport);
    }

    private EnqueueResultDTO enqueueRuns(Set<String> candidates, Set<String> knownIds, String playlistLink) {
        Set<String> enqueued = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        ImportBatchKind kind = playlistLink == null || playlistLink.isBlank()
                ? ImportBatchKind.ADMIN_IDS : ImportBatchKind.ADMIN_PLAYLIST;
        long batch = pipeline.createBatch(kind, "Admin catalog seed", playlistLink, null);
        for (String youtubeId : candidates) {
            PipelineRun run = pipeline.startRun(youtubeId, PipelineLane.BACKFILL,
                    PendingImportOrigin.ADMIN_SEED, batch, null);
            if (java.util.Objects.equals(run.batchId(), batch)) {
                enqueued.add(youtubeId);
            } else {
                duplicates.add(youtubeId);
            }
        }
        pipeline.recordSkippedDuplicates(batch, knownIds.size() + duplicates.size());
        return new EnqueueResultDTO(enqueued, knownIds, duplicates, batch);
    }
}
