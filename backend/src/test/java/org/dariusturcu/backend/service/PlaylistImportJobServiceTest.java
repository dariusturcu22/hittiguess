package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistImportJob;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobDTO;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItem;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItemStatus;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobStatus;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistImportJobItemRepository;
import org.dariusturcu.backend.repository.PlaylistImportJobRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.websocket.BulkImportProgressEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistImportJobServiceTest {

    private static final Long PLAYLIST_ID = 7L;
    private static final Long SUBMITTING_USER_ID = 11L;
    private static final String SUBMITTING_USERNAME = "importer";

    @Mock
    private PlaylistImportJobRepository jobRepository;
    @Mock
    private PlaylistImportJobItemRepository itemRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlaylistAccessService playlistAccessService;
    @Mock
    private YoutubeIdLookupService youtubeIdLookupService;
    @Mock
    private SongResolutionService songResolutionService;
    @Mock
    private CatalogSeedingService catalogSeedingService;
    @Mock
    private MetadataPriorityCoordinator metadataPriorityCoordinator;
    @Mock
    private PlaylistExpansionService playlistExpansionService;
    @Mock
    private PlaylistImportService playlistImportService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private PlaylistImportJobService service() {
        return new PlaylistImportJobService(
                jobRepository, itemRepository, playlistRepository, userRepository,
                playlistAccessService, youtubeIdLookupService, songResolutionService,
                catalogSeedingService, metadataPriorityCoordinator, playlistExpansionService,
                playlistImportService, applicationEventPublisher, new SyncTaskExecutor());
    }

    private User submittingUser() {
        User user = new User();
        user.setId(SUBMITTING_USER_ID);
        user.setUsername(SUBMITTING_USERNAME);
        user.setRole(Role.USER);
        return user;
    }

    private Song songWithId(Long songId) {
        Song song = Mockito.mock(Song.class);
        when(song.getId()).thenReturn(songId);
        return song;
    }

    @Test
    void startImportPersistsItemsAndResolvesThemOffThread() {
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
        when(userRepository.findById(SUBMITTING_USER_ID)).thenReturn(Optional.of(submittingUser()));
        when(playlistExpansionService.expandAndMerge(any(), any())).thenReturn(List.of("video-1", "video-2"));
        when(youtubeIdLookupService.partitionKnownAndUnknown(any()))
                .thenReturn(new YoutubeIdLookupResult(Set.of("video-1"), Set.of("video-2")));
        Song resolvedSong = songWithId(102L);
        when(youtubeIdLookupService.resolveCanonicalSongIds(any())).thenReturn(Map.of("video-1", 101L));
        when(songResolutionService.resolveAndPersist(eq("video-2"), any(User.class))).thenReturn(Optional.of(resolvedSong));

        List<PlaylistImportJobItem> savedItems = new ArrayList<>();
        when(itemRepository.save(any(PlaylistImportJobItem.class))).thenAnswer(invocation -> {
            PlaylistImportJobItem item = invocation.getArgument(0);
            if (!savedItems.contains(item)) {
                savedItems.add(item);
            }
            return item;
        });
        when(itemRepository.findByJobIdOrderByIdAsc(any())).thenAnswer(invocation -> List.copyOf(savedItems));
        PlaylistImportJob storedJob = new PlaylistImportJob();
        when(jobRepository.findById(any())).thenReturn(Optional.of(storedJob));

        String jobId;
        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(submittingUser());
            jobId = service().startImport(
                    PLAYLIST_ID, new StartPlaylistImportRequest(List.of("video-1", "video-2"), null));
        }

        assertThat(jobId).isNotBlank();
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems.get(0).getStatus()).isEqualTo(PlaylistImportJobItemStatus.ALREADY_KNOWN);
        assertThat(savedItems.get(0).getSongId()).isEqualTo(101L);
        assertThat(savedItems.get(1).getStatus()).isEqualTo(PlaylistImportJobItemStatus.RESOLVED);
        assertThat(savedItems.get(1).getSongId()).isEqualTo(102L);
        assertThat(storedJob.getStatus()).isEqualTo(PlaylistImportJobStatus.DONE);
        assertThat(storedJob.getCompletedAt()).isNotNull();

        ArgumentCaptor<BulkImportProgressEvent> progressEvents = ArgumentCaptor.forClass(BulkImportProgressEvent.class);
        verify(applicationEventPublisher, Mockito.times(2)).publishEvent(progressEvents.capture());
        assertThat(progressEvents.getAllValues())
                .extracting(BulkImportProgressEvent::youtubeId)
                .containsExactlyInAnyOrder("video-1", "video-2");
        verify(playlistImportService).addResolvedSongIds(PLAYLIST_ID, List.of(101L, 102L));
    }

    @Test
    void activeImportReturnsTheRunningJob() {
        PlaylistImportJob runningJob = new PlaylistImportJob();
        runningJob.setId("job-1");
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        runningJob.setPlaylist(playlist);
        runningJob.setStatus(PlaylistImportJobStatus.RUNNING);
        when(jobRepository.findByPlaylistIdAndStatusOrderByCreatedAtDesc(PLAYLIST_ID, PlaylistImportJobStatus.RUNNING))
                .thenReturn(List.of(runningJob));
        when(itemRepository.findByJobIdOrderByIdAsc("job-1")).thenReturn(List.of());

        Optional<PlaylistImportJobDTO> activeImport = service().findActiveImport(PLAYLIST_ID);

        assertThat(activeImport).isPresent();
        assertThat(activeImport.get().id()).isEqualTo("job-1");
        assertThat(activeImport.get().playlistId()).isEqualTo(PLAYLIST_ID);
    }

    @Test
    void activeImportIsEmptyWithoutARunningJob() {
        when(jobRepository.findByPlaylistIdAndStatusOrderByCreatedAtDesc(PLAYLIST_ID, PlaylistImportJobStatus.RUNNING))
                .thenReturn(List.of());

        assertThat(service().findActiveImport(PLAYLIST_ID)).isEmpty();
    }
}
