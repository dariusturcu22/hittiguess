package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistImportJob;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobDTO;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItem;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItemDTO;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItemStatus;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobStatus;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistImportJobItemRepository;
import org.dariusturcu.backend.repository.PlaylistImportJobRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.dariusturcu.backend.websocket.BulkImportProgressEvent;
import org.dariusturcu.backend.websocket.BulkImportProgressOutcome;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Playlist-scoped background imports. The HTTP call only parses the submitted ids,
 * persists a RUNNING job with one PENDING item per id, and returns the job id; the
 * slow YouTube resolution runs on the import executor and lands each outcome on its
 * item row, reusing the on-the-spot lookup, resolution, linking, and progress-event
 * flow so live clients see the same per-song updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistImportJobService {

    private final PlaylistImportJobRepository jobRepository;
    private final PlaylistImportJobItemRepository itemRepository;
    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final PlaylistAccessService playlistAccessService;
    private final YoutubeIdLookupService youtubeIdLookupService;
    private final SongResolutionService songResolutionService;
    private final CatalogSeedingService catalogSeedingService;
    private final MetadataPriorityCoordinator metadataPriorityCoordinator;
    private final PlaylistExpansionService playlistExpansionService;
    private final PlaylistImportService playlistImportService;
    private final ApplicationEventPublisher applicationEventPublisher;
    @Qualifier("importJobExecutor")
    private final TaskExecutor importJobExecutor;

    @Transactional
    public String startImport(Long playlistId, StartPlaylistImportRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        playlistAccessService.requireWrite(playlist, currentUser);

        List<String> parsedYoutubeIds = YoutubeLinkParser.parseAllVideoIds(
                request.videoIdsOrLinks() == null ? List.of() : request.videoIdsOrLinks());
        List<String> mergedYoutubeIds = playlistExpansionService.expandAndMerge(request.playlistLink(), parsedYoutubeIds);

        PlaylistImportJob job = new PlaylistImportJob();
        String jobId = UUID.randomUUID().toString();
        job.setId(jobId);
        job.setPlaylist(playlist);
        job.setSubmittedByUsername(currentUser.getUsername());
        job.setStatus(PlaylistImportJobStatus.RUNNING);
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        for (String youtubeId : mergedYoutubeIds) {
            PlaylistImportJobItem item = new PlaylistImportJobItem();
            item.setJob(job);
            item.setYoutubeId(youtubeId);
            item.setStatus(PlaylistImportJobItemStatus.PENDING);
            itemRepository.save(item);
        }

        Long submittingUserId = currentUser.getId();
        String submittingUsername = currentUser.getUsername();
        importJobExecutor.execute(() -> runImport(jobId, playlistId, submittingUserId, submittingUsername));
        return jobId;
    }

    @Transactional(readOnly = true)
    public Optional<PlaylistImportJobDTO> findActiveImport(Long playlistId) {
        List<PlaylistImportJob> runningJobs =
                jobRepository.findByPlaylistIdAndStatusOrderByCreatedAtDesc(playlistId, PlaylistImportJobStatus.RUNNING);
        if (runningJobs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toDTO(runningJobs.get(0)));
    }

    void runImport(String jobId, Long playlistId, Long submittingUserId, String submittingUsername) {
        User submittingUser = userRepository.findById(submittingUserId).orElse(null);
        if (submittingUser == null) {
            finishJob(jobId, PlaylistImportJobStatus.FAILED);
            return;
        }
        UserPrincipal principal = new UserPrincipal(submittingUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            List<PlaylistImportJobItem> items = itemRepository.findByJobIdOrderByIdAsc(jobId);

            YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(
                    items.stream().map(PlaylistImportJobItem::getYoutubeId).toList());
            Set<String> knownIdSet = new HashSet<>(lookupResult.knownYoutubeIds());
            Map<String, Long> knownSongIds = youtubeIdLookupService.resolveCanonicalSongIds(lookupResult.knownYoutubeIds());
            List<Song> knownSongs = youtubeIdLookupService.resolveCanonicalSongs(lookupResult.knownYoutubeIds());
            for (PlaylistImportJobItem item : items) {
                if (!knownIdSet.contains(item.getYoutubeId())) {
                    continue;
                }
                item.setStatus(PlaylistImportJobItemStatus.ALREADY_KNOWN);
                item.setSongId(knownSongIds.get(item.getYoutubeId()));
                itemRepository.save(item);
                publishProgress(submittingUsername, jobId, item.getYoutubeId(), BulkImportProgressOutcome.ALREADY_KNOWN);
            }

            List<Song> resolvedSongs = new ArrayList<>();
            metadataPriorityCoordinator.beginOnTheSpotWork();
            try {
                for (PlaylistImportJobItem item : items) {
                    if (item.getStatus() != PlaylistImportJobItemStatus.PENDING) {
                        continue;
                    }
                    Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(item.getYoutubeId());
                    if (resolvedSong.isPresent()) {
                        item.setStatus(PlaylistImportJobItemStatus.RESOLVED);
                        item.setSongId(resolvedSong.get().getId());
                        resolvedSongs.add(resolvedSong.get());
                        catalogSeedingService.reEnqueueForPatientReprocessing(item.getYoutubeId());
                        publishProgress(submittingUsername, jobId, item.getYoutubeId(), BulkImportProgressOutcome.RESOLVED);
                    } else {
                        item.setStatus(PlaylistImportJobItemStatus.UNRESOLVED);
                        publishProgress(submittingUsername, jobId, item.getYoutubeId(), BulkImportProgressOutcome.UNRESOLVED);
                    }
                    itemRepository.save(item);
                }
            } finally {
                metadataPriorityCoordinator.endOnTheSpotWork();
            }

            List<Song> songsToLink = new ArrayList<>(knownSongs);
            songsToLink.addAll(resolvedSongs);
            playlistImportService.addResolvedSongs(playlistId, songsToLink);
            finishJob(jobId, PlaylistImportJobStatus.DONE);
        } catch (RuntimeException failure) {
            log.warn("Background playlist import {} failed", jobId, failure);
            finishJob(jobId, PlaylistImportJobStatus.FAILED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Transactional
    void finishJob(String jobId, PlaylistImportJobStatus status) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        });
    }

    private PlaylistImportJobDTO toDTO(PlaylistImportJob job) {
        List<PlaylistImportJobItemDTO> items = itemRepository.findByJobIdOrderByIdAsc(job.getId()).stream()
                .map(item -> new PlaylistImportJobItemDTO(item.getYoutubeId(), item.getStatus(), item.getSongId()))
                .toList();
        return new PlaylistImportJobDTO(
                job.getId(),
                job.getPlaylist().getId(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                items);
    }

    private void publishProgress(String username, String importJobId, String youtubeId, BulkImportProgressOutcome outcome) {
        applicationEventPublisher.publishEvent(new BulkImportProgressEvent(username, importJobId, youtubeId, outcome));
    }
}
