package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.BulkImportResultDTO;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.dariusturcu.backend.websocket.BulkImportProgressEvent;
import org.dariusturcu.backend.websocket.BulkImportProgressOutcome;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The user on-the-spot bulk-import path. It shares the batch YouTube-ID lookup with
 * the admin backlog, resolves only genuinely new IDs, and does so immediately,
 * independent of the admin backlog's schedule, even when the same ID is also sitting
 * in that backlog waiting its turn. New IDs go through the fast tier in parallel via
 * FastTierImportRunner, which also brackets the work as high priority and queues each
 * provisional answer for the patient recheck.
 *
 * Publishes a BulkImportProgressEvent for every submitted video id, already-known ones
 * included, so the submitting user's client can render live per-song progress against
 * the full set it asked to import rather than only the ones this loop actively resolves.
 *
 * When the request carries a targetPlaylistId, every song this call resolves or finds
 * already known is also linked into that playlist, so an import started from a specific
 * playlist's page actually lands its songs there rather than only growing the catalog.
 * Write access to that playlist, the import size cap, and the daily new-song quota are all
 * checked before any paid lookup runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final YoutubeIdLookupService youtubeIdLookupService;
    private final FastTierImportRunner fastTierImportRunner;
    private final PlaylistExpansionService playlistExpansionService;
    private final PlaylistImportService playlistImportService;
    private final ImportQuotaService importQuotaService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public BulkImportResultDTO importImmediately(BulkImportRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        String submittingUsername = currentUser.getUsername();
        if (request.targetPlaylistId() != null) {
            playlistImportService.requireWritableTarget(request.targetPlaylistId());
        }
        List<String> parsedYoutubeIds = YoutubeLinkParser.parseAllVideoIds(request.videoIdsOrLinks());
        importQuotaService.requireWithinImportSize(parsedYoutubeIds.size());
        List<String> mergedYoutubeIds = playlistExpansionService.expandAndMerge(request.playlistLink(), parsedYoutubeIds);
        importQuotaService.requireWithinImportSize(mergedYoutubeIds.size());

        YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(mergedYoutubeIds);
        importQuotaService.reserveNewSongResolutions(currentUser.getId(), lookupResult.unknownYoutubeIds().size());

        for (String alreadyKnownId : lookupResult.knownYoutubeIds()) {
            publishProgress(submittingUsername, request.importJobId(), alreadyKnownId, BulkImportProgressOutcome.ALREADY_KNOWN);
        }

        Set<String> resolvedIds = ConcurrentHashMap.newKeySet();
        Set<String> unresolvedIds = ConcurrentHashMap.newKeySet();
        List<Song> resolvedSongs = new CopyOnWriteArrayList<>();

        fastTierImportRunner.resolveAll(List.copyOf(lookupResult.unknownYoutubeIds()), currentUser, new FastTierImportRunner.Listener() {
            @Override
            public void identifying(String youtubeId) {
            }

            @Override
            public void dating(String youtubeId) {
            }

            @Override
            public void resolved(String youtubeId, Song song) {
                resolvedIds.add(youtubeId);
                resolvedSongs.add(song);
                publishProgress(submittingUsername, request.importJobId(), youtubeId, BulkImportProgressOutcome.RESOLVED);
            }

            @Override
            public void unresolved(String youtubeId) {
                unresolvedIds.add(youtubeId);
                publishProgress(submittingUsername, request.importJobId(), youtubeId, BulkImportProgressOutcome.UNRESOLVED);
            }
        });

        if (request.targetPlaylistId() != null) {
            List<Song> knownSongs = youtubeIdLookupService.resolveCanonicalSongs(lookupResult.knownYoutubeIds());
            List<Song> songsToLink = new ArrayList<>(knownSongs);
            songsToLink.addAll(resolvedSongs);
            playlistImportService.addResolvedSongs(request.targetPlaylistId(), songsToLink);
        }

        return new BulkImportResultDTO(lookupResult.knownYoutubeIds(), orderedAsSubmitted(resolvedIds, lookupResult),
                orderedAsSubmitted(unresolvedIds, lookupResult));
    }

    private Set<String> orderedAsSubmitted(Set<String> youtubeIds, YoutubeIdLookupResult lookupResult) {
        Set<String> ordered = new LinkedHashSet<>();
        lookupResult.unknownYoutubeIds().stream().filter(youtubeIds::contains).forEach(ordered::add);
        return ordered;
    }

    private void publishProgress(String username, String importJobId, String youtubeId, BulkImportProgressOutcome outcome) {
        applicationEventPublisher.publishEvent(new BulkImportProgressEvent(username, importJobId, youtubeId, outcome));
    }
}
