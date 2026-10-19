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
import org.dariusturcu.backend.model.ai.VideoInfoItem;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistImportJobItemRepository;
import org.dariusturcu.backend.repository.PlaylistImportJobRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.SongArtistFormatter;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Playlist-scoped background imports. The HTTP call only parses the submitted ids,
 * persists a RUNNING job with one PENDING item per id, and returns the job id. The
 * import then runs on the import executor: songs the catalog already knows are linked
 * at once, and every new song goes through the fast tier in parallel, moving its item
 * through IDENTIFYING and DATING and joining the playlist the moment it resolves, with
 * the same progress events live clients already follow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistImportJobService {

    private final PlaylistImportJobRepository jobRepository;
    private final PlaylistImportJobItemRepository itemRepository;
    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final PlaylistAccessService playlistAccessService;
    private final YoutubeIdLookupService youtubeIdLookupService;
    private final FastTierImportRunner fastTierImportRunner;
    private final PlaylistExpansionService playlistExpansionService;
    private final PlaylistImportService playlistImportService;
    private final ImportQuotaService importQuotaService;
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
        importQuotaService.requireWithinImportSize(parsedYoutubeIds.size());
        List<String> mergedYoutubeIds = playlistExpansionService.expandAndMerge(request.playlistLink(), parsedYoutubeIds);
        importQuotaService.requireWithinImportSize(mergedYoutubeIds.size());
        YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(mergedYoutubeIds);
        importQuotaService.reserveNewSongResolutions(currentUser.getId(), lookupResult.unknownYoutubeIds().size());
        Map<String, VideoInfoItem> videoInfoByYoutubeId = playlistExpansionService.fetchVideoInfo(mergedYoutubeIds);

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
            VideoInfoItem videoInfo = videoInfoByYoutubeId.get(youtubeId);
            if (videoInfo != null) {
                item.setRawTitle(videoInfo.title());
                item.setRawChannelTitle(videoInfo.channelTitle());
            }
            itemRepository.save(item);
        }

        Long submittingUserId = currentUser.getId();
        String submittingUsername = currentUser.getUsername();
        importJobExecutor.execute(() -> runImport(jobId, playlistId, submittingUserId, submittingUsername));
        return jobId;
    }

    @Transactional(readOnly = true)
    // Checked before looking for a job, so a playlist the caller can't read gives the
    // same refusal whether or not an import is running on it.
    public Optional<PlaylistImportJobDTO> findActiveImport(Long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());
        List<PlaylistImportJob> runningJobs =
                jobRepository.findByPlaylistIdAndStatusOrderByCreatedAtDesc(playlistId, PlaylistImportJobStatus.RUNNING);
        return runningJobs.stream().findFirst().map(this::toDTO);
    }

    // A finished job stops being the playlist's active import, so the import screen reads
    // its final results through this instead. Needs read access to the playlist.
    @Transactional(readOnly = true)
    public PlaylistImportJobDTO findImport(Long playlistId, String importJobId) {
        PlaylistImportJob job = jobRepository.findById(importJobId)
                .filter(candidate -> candidate.getPlaylist().getId().equals(playlistId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import job not found"));
        playlistAccessService.requireRead(job.getPlaylist(), SecurityUtils.getCurrentUser());
        return toDTO(job);
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
            Map<String, Long> itemIdsByYoutubeId = items.stream()
                    .collect(Collectors.toMap(PlaylistImportJobItem::getYoutubeId, PlaylistImportJobItem::getId,
                            (firstItemId, repeatedItemId) -> firstItemId));

            YoutubeIdLookupResult lookupResult = youtubeIdLookupService.partitionKnownAndUnknown(itemIdsByYoutubeId.keySet());
            Map<String, Long> knownSongIds = youtubeIdLookupService.resolveCanonicalSongIds(lookupResult.knownYoutubeIds());
            for (Map.Entry<String, Long> knownSong : knownSongIds.entrySet()) {
                updateItem(itemIdsByYoutubeId.get(knownSong.getKey()), PlaylistImportJobItemStatus.ALREADY_KNOWN, knownSong.getValue());
                publishProgress(submittingUsername, jobId, knownSong.getKey(), BulkImportProgressOutcome.ALREADY_KNOWN);
            }
            playlistImportService.addResolvedSongIds(playlistId, List.copyOf(knownSongIds.values()));

            List<String> newYoutubeIds = itemIdsByYoutubeId.keySet().stream()
                    .filter(youtubeId -> !knownSongIds.containsKey(youtubeId))
                    .toList();
            Object playlistLinkLock = new Object();
            fastTierImportRunner.resolveAll(newYoutubeIds, submittingUser, new FastTierImportRunner.Listener() {
                @Override
                public void identifying(String youtubeId) {
                    updateItem(itemIdsByYoutubeId.get(youtubeId), PlaylistImportJobItemStatus.IDENTIFYING, null);
                }

                @Override
                public void dating(String youtubeId) {
                    updateItem(itemIdsByYoutubeId.get(youtubeId), PlaylistImportJobItemStatus.DATING, null);
                }

                // Each song joins the playlist the moment it resolves. Links are made one
                // at a time so concurrent songs never rewrite the playlist's song list
                // over each other.
                @Override
                public void resolved(String youtubeId, Song song) {
                    synchronized (playlistLinkLock) {
                        playlistImportService.addResolvedSongIds(playlistId, List.of(song.getId()));
                    }
                    updateItem(itemIdsByYoutubeId.get(youtubeId), PlaylistImportJobItemStatus.RESOLVED, song.getId());
                    publishProgress(submittingUsername, jobId, youtubeId, BulkImportProgressOutcome.RESOLVED);
                }

                @Override
                public void unresolved(String youtubeId) {
                    updateItem(itemIdsByYoutubeId.get(youtubeId), PlaylistImportJobItemStatus.UNRESOLVED, null);
                    publishProgress(submittingUsername, jobId, youtubeId, BulkImportProgressOutcome.UNRESOLVED);
                }
            });
            finishJob(jobId, PlaylistImportJobStatus.DONE);
        } catch (RuntimeException failure) {
            log.warn("Background playlist import {} failed", jobId, failure);
            finishJob(jobId, PlaylistImportJobStatus.FAILED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // Workers update their own items concurrently, so each update reads the row fresh
    // rather than touching an entity another thread loaded.
    private void updateItem(Long itemId, PlaylistImportJobItemStatus status, Long songId) {
        itemRepository.findById(itemId).ifPresent(item -> {
            item.setStatus(status);
            if (songId != null) {
                item.setSongId(songId);
            }
            itemRepository.save(item);
        });
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
        List<PlaylistImportJobItem> jobItems = itemRepository.findByJobIdOrderByIdAsc(job.getId());
        List<Long> resolvedSongIds = jobItems.stream()
                .map(PlaylistImportJobItem::getSongId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Song> songsById = songRepository.findAllById(resolvedSongIds).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));

        List<PlaylistImportJobItemDTO> items = jobItems.stream()
                .map(item -> {
                    Song resolvedSong = item.getSongId() == null ? null : songsById.get(item.getSongId());
                    return new PlaylistImportJobItemDTO(
                            item.getYoutubeId(),
                            item.getStatus(),
                            item.getSongId(),
                            item.getRawTitle(),
                            item.getRawChannelTitle(),
                            resolvedSong == null ? null : resolvedSong.getTitle(),
                            resolvedSong == null ? null : SongArtistFormatter.formatCredit(resolvedSong),
                            resolvedSong == null ? null : resolvedSong.getReleaseYear(),
                            resolvedSong == null ? null : resolvedSong.getColor());
                })
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
