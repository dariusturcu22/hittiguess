package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.BulkImportResultDTO;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.dariusturcu.backend.websocket.BulkImportProgressEvent;
import org.dariusturcu.backend.websocket.BulkImportProgressOutcome;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The user on-the-spot bulk-import path. It shares the batch YouTube-ID lookup with
 * the admin backlog, resolves only genuinely new IDs, and does so immediately,
 * independent of the admin backlog's schedule, even when the same ID is also sitting
 * in that backlog waiting its turn. On-the-spot work is bracketed as high priority
 * through the coordinator so the backlog drain yields the shared external rate-limit
 * budget while it runs. Every ID resolved here is also re-enqueued into the admin
 * backlog so the patient pipeline reprocesses the provisional fast-tier answer.
 *
 * Publishes a BulkImportProgressEvent for every submitted video id, already-known ones
 * included, so the submitting user's client can render live per-song progress against
 * the full set it asked to import rather than only the ones this loop actively resolves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final YoutubeIdLookupService youtubeIdLookupService;
    private final SongResolutionService songResolutionService;
    private final CatalogSeedingService catalogSeedingService;
    private final MetadataPriorityCoordinator metadataPriorityCoordinator;
    private final PlaylistExpansionService playlistExpansionService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public BulkImportResultDTO importImmediately(BulkImportRequest request) {
        String submittingUsername = SecurityUtils.getCurrentUser().getUsername();
        List<String> parsedYoutubeIds = YoutubeLinkParser.parseAllVideoIds(request.videoIdsOrLinks());
        List<String> mergedYoutubeIds = playlistExpansionService.expandAndMerge(request.playlistLink(), parsedYoutubeIds);

        YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(mergedYoutubeIds);

        for (String alreadyKnownId : lookupResult.knownYoutubeIds()) {
            publishProgress(submittingUsername, alreadyKnownId, BulkImportProgressOutcome.ALREADY_KNOWN);
        }

        Set<String> resolvedIds = new LinkedHashSet<>();
        Set<String> unresolvedIds = new LinkedHashSet<>();

        metadataPriorityCoordinator.beginOnTheSpotWork();
        try {
            for (String youtubeId : lookupResult.unknownYoutubeIds()) {
                boolean resolved = songResolutionService.resolveAndPersist(youtubeId).isPresent();
                if (resolved) {
                    resolvedIds.add(youtubeId);
                    catalogSeedingService.reEnqueueForPatientReprocessing(youtubeId);
                    publishProgress(submittingUsername, youtubeId, BulkImportProgressOutcome.RESOLVED);
                } else {
                    unresolvedIds.add(youtubeId);
                    publishProgress(submittingUsername, youtubeId, BulkImportProgressOutcome.UNRESOLVED);
                }
            }
        } finally {
            metadataPriorityCoordinator.endOnTheSpotWork();
        }

        return new BulkImportResultDTO(lookupResult.knownYoutubeIds(), resolvedIds, unresolvedIds);
    }

    private void publishProgress(String username, String youtubeId, BulkImportProgressOutcome outcome) {
        applicationEventPublisher.publishEvent(new BulkImportProgressEvent(username, youtubeId, outcome));
    }
}
