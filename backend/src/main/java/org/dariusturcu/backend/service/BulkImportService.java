package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.BulkImportResultDTO;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The user on-the-spot bulk-import path. It shares the batch YouTube-ID lookup with
 * the admin backlog, resolves only genuinely new IDs, and does so immediately,
 * independent of the admin backlog's schedule, even when the same ID is also sitting
 * in that backlog waiting its turn. On-the-spot work is bracketed as high priority
 * through the coordinator so the backlog drain yields the shared external rate-limit
 * budget while it runs. Every ID resolved here is also re-enqueued into the admin
 * backlog so the patient pipeline reprocesses the provisional fast-tier answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final YoutubeIdLookupService youtubeIdLookupService;
    private final SongResolutionService songResolutionService;
    private final CatalogSeedingService catalogSeedingService;
    private final MetadataPriorityCoordinator metadataPriorityCoordinator;

    public BulkImportResultDTO importImmediately(BulkImportRequest request) {
        List<String> parsedYoutubeIds = parseSubmittedInputs(request.videoIdsOrLinks());
        YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(parsedYoutubeIds);

        Set<String> resolvedIds = new LinkedHashSet<>();
        Set<String> unresolvedIds = new LinkedHashSet<>();

        metadataPriorityCoordinator.beginOnTheSpotWork();
        try {
            for (String youtubeId : lookupResult.unknownYoutubeIds()) {
                boolean resolved = songResolutionService.resolveAndPersist(youtubeId).isPresent();
                if (resolved) {
                    resolvedIds.add(youtubeId);
                    catalogSeedingService.reEnqueueForPatientReprocessing(youtubeId);
                } else {
                    unresolvedIds.add(youtubeId);
                }
            }
        } finally {
            metadataPriorityCoordinator.endOnTheSpotWork();
        }

        return new BulkImportResultDTO(lookupResult.knownYoutubeIds(), resolvedIds, unresolvedIds);
    }

    private List<String> parseSubmittedInputs(List<String> videoIdsOrLinks) {
        if (videoIdsOrLinks == null) {
            return List.of();
        }
        List<String> parsedIds = new ArrayList<>();
        for (String submittedInput : videoIdsOrLinks) {
            Optional<String> parsedId = YoutubeLinkParser.parseVideoId(submittedInput);
            parsedId.ifPresent(parsedIds::add);
        }
        return parsedIds;
    }
}
