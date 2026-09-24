package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.PlaylistImportException;
import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistImportJob;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobDTO;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItem;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItemStatus;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobStatus;
import org.dariusturcu.backend.model.ai.VideoInfoItem;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistImportJobItemRepository;
import org.dariusturcu.backend.repository.PlaylistImportJobRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistImportJobServiceTest {

    private static final Long PLAYLIST_ID = 7L;
    private static final Long SUBMITTING_USER_ID = 11L;
    private static final String SUBMITTING_USERNAME = "importer";
    private static final String KNOWN_VIDEO_ID = "knownVideo1";
    private static final String NEW_VIDEO_ID = "newVideoId1";
    private static final int ONE_NEW_SONG = 1;

    @Mock
    private PlaylistImportJobRepository jobRepository;
    @Mock
    private PlaylistImportJobItemRepository itemRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private SongRepository songRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlaylistAccessService playlistAccessService;
    @Mock
    private YoutubeIdLookupService youtubeIdLookupService;
    @Mock
    private FastTierImportRunner fastTierImportRunner;
    @Mock
    private PlaylistExpansionService playlistExpansionService;
    @Mock
    private PlaylistImportService playlistImportService;
    @Mock
    private ImportQuotaService importQuotaService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private PlaylistImportJobService service() {
        return new PlaylistImportJobService(
                jobRepository, itemRepository, playlistRepository, songRepository, userRepository,
                playlistAccessService, youtubeIdLookupService, fastTierImportRunner, playlistExpansionService,
                playlistImportService, importQuotaService, applicationEventPublisher, new SyncTaskExecutor());
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
    void startImportLinksKnownSongsAtOnceAndEachNewSongTheMomentItResolves() {
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
        when(userRepository.findById(SUBMITTING_USER_ID)).thenReturn(Optional.of(submittingUser()));
        when(playlistExpansionService.expandAndMerge(any(), any())).thenReturn(List.of("video-1", "video-2"));
        when(playlistExpansionService.fetchVideoInfo(any())).thenReturn(Map.of(
                "video-2", new VideoInfoItem("video-2", "Raw Video Title", "Uploading Channel")));
        when(youtubeIdLookupService.partitionKnownAndUnknown(any()))
                .thenReturn(new YoutubeIdLookupResult(Set.of("video-1"), Set.of("video-2")));
        Song resolvedSong = songWithId(102L);
        when(youtubeIdLookupService.resolveCanonicalSongIds(any())).thenReturn(Map.of("video-1", 101L));
        List<PlaylistImportJobItemStatus> newSongStatusHistory = new ArrayList<>();
        doAnswer(invocation -> {
            List<String> youtubeIds = invocation.getArgument(0);
            FastTierImportRunner.Listener listener = invocation.getArgument(2);
            assertThat(youtubeIds).containsExactly("video-2");
            listener.identifying("video-2");
            listener.dating("video-2");
            verify(playlistImportService, never()).addResolvedSongIds(PLAYLIST_ID, List.of(102L));
            listener.resolved("video-2", resolvedSong);
            verify(playlistImportService).addResolvedSongIds(PLAYLIST_ID, List.of(102L));
            return null;
        }).when(fastTierImportRunner).resolveAll(any(), any(User.class), any(FastTierImportRunner.Listener.class));

        List<PlaylistImportJobItem> savedItems = new ArrayList<>();
        when(itemRepository.save(any(PlaylistImportJobItem.class))).thenAnswer(invocation -> {
            PlaylistImportJobItem item = invocation.getArgument(0);
            if (!savedItems.contains(item)) {
                item.setId((long) savedItems.size() + 1);
                savedItems.add(item);
            }
            if ("video-2".equals(item.getYoutubeId())) {
                newSongStatusHistory.add(item.getStatus());
            }
            return item;
        });
        when(itemRepository.findById(any())).thenAnswer(invocation -> savedItems.stream()
                .filter(item -> item.getId().equals(invocation.getArgument(0)))
                .findFirst());
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
        assertThat(savedItems.get(1).getRawTitle()).isEqualTo("Raw Video Title");
        assertThat(savedItems.get(1).getRawChannelTitle()).isEqualTo("Uploading Channel");
        assertThat(savedItems.get(0).getRawTitle()).isNull();
        assertThat(storedJob.getStatus()).isEqualTo(PlaylistImportJobStatus.DONE);
        assertThat(storedJob.getCompletedAt()).isNotNull();

        ArgumentCaptor<BulkImportProgressEvent> progressEvents = ArgumentCaptor.forClass(BulkImportProgressEvent.class);
        verify(applicationEventPublisher, Mockito.times(2)).publishEvent(progressEvents.capture());
        assertThat(progressEvents.getAllValues())
                .extracting(BulkImportProgressEvent::youtubeId)
                .containsExactlyInAnyOrder("video-1", "video-2");
        verify(playlistImportService).addResolvedSongIds(PLAYLIST_ID, List.of(101L));
        assertThat(newSongStatusHistory).containsExactly(
                PlaylistImportJobItemStatus.PENDING,
                PlaylistImportJobItemStatus.IDENTIFYING,
                PlaylistImportJobItemStatus.DATING,
                PlaylistImportJobItemStatus.RESOLVED);
    }

    @Test
    void startImportReservesOnlyTheSongsNotYetInTheCatalog() {
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
        when(playlistExpansionService.expandAndMerge(any(), any())).thenReturn(List.of(KNOWN_VIDEO_ID, NEW_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(any()))
                .thenReturn(new YoutubeIdLookupResult(Set.of(KNOWN_VIDEO_ID), Set.of(NEW_VIDEO_ID)));
        doThrow(new RateLimitExceededException("Daily limit reached"))
                .when(importQuotaService).reserveNewSongResolutions(SUBMITTING_USER_ID, ONE_NEW_SONG);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(submittingUser());
            PlaylistImportJobService importJobService = service();
            StartPlaylistImportRequest request = new StartPlaylistImportRequest(List.of(KNOWN_VIDEO_ID, NEW_VIDEO_ID), null);

            assertThatThrownBy(() -> importJobService.startImport(PLAYLIST_ID, request))
                    .isInstanceOf(RateLimitExceededException.class);
        }

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(fastTierImportRunner);
    }

    @Test
    void startImportRefusesAnOversizedSubmissionBeforeExpandingOrPersisting() {
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
        doThrow(new PlaylistImportException("Too many songs"))
                .when(importQuotaService).requireWithinImportSize(ONE_NEW_SONG);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(submittingUser());
            PlaylistImportJobService importJobService = service();
            StartPlaylistImportRequest request = new StartPlaylistImportRequest(List.of(NEW_VIDEO_ID), "playlist-link");

            assertThatThrownBy(() -> importJobService.startImport(PLAYLIST_ID, request))
                    .isInstanceOf(PlaylistImportException.class);
        }

        verifyNoInteractions(playlistExpansionService);
        verify(jobRepository, never()).save(any());
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
    void activeImportItemsCarryResolvedSongDetailsAndRawVideoInfoByStatus() {
        PlaylistImportJob runningJob = new PlaylistImportJob();
        runningJob.setId("job-1");
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        runningJob.setPlaylist(playlist);
        runningJob.setStatus(PlaylistImportJobStatus.RUNNING);
        when(jobRepository.findByPlaylistIdAndStatusOrderByCreatedAtDesc(PLAYLIST_ID, PlaylistImportJobStatus.RUNNING))
                .thenReturn(List.of(runningJob));

        PlaylistImportJobItem resolvedItem = new PlaylistImportJobItem();
        resolvedItem.setYoutubeId("video-1");
        resolvedItem.setStatus(PlaylistImportJobItemStatus.RESOLVED);
        resolvedItem.setSongId(101L);

        PlaylistImportJobItem pendingItem = new PlaylistImportJobItem();
        pendingItem.setYoutubeId("video-2");
        pendingItem.setStatus(PlaylistImportJobItemStatus.PENDING);
        pendingItem.setRawTitle("Raw Video Title");
        pendingItem.setRawChannelTitle("Uploading Channel");

        when(itemRepository.findByJobIdOrderByIdAsc("job-1")).thenReturn(List.of(resolvedItem, pendingItem));

        Song resolvedSong = new Song();
        resolvedSong.setId(101L);
        resolvedSong.setTitle("Resolved Title");
        resolvedSong.setReleaseYear(2011);
        resolvedSong.setColor("abcdef");
        SongArtist mainArtist = new SongArtist();
        mainArtist.setName("Main Artist");
        mainArtist.setRole(ArtistRole.MAIN);
        resolvedSong.getArtists().add(mainArtist);
        when(songRepository.findAllById(List.of(101L))).thenReturn(List.of(resolvedSong));

        Optional<PlaylistImportJobDTO> activeImport = service().findActiveImport(PLAYLIST_ID);

        assertThat(activeImport).isPresent();
        var items = activeImport.get().items();
        assertThat(items.get(0).resolvedTitle()).isEqualTo("Resolved Title");
        assertThat(items.get(0).resolvedArtists()).isEqualTo("Main Artist");
        assertThat(items.get(0).resolvedReleaseYear()).isEqualTo(2011);
        assertThat(items.get(0).resolvedColor()).isEqualTo("abcdef");
        assertThat(items.get(0).rawTitle()).isNull();

        assertThat(items.get(1).resolvedTitle()).isNull();
        assertThat(items.get(1).rawTitle()).isEqualTo("Raw Video Title");
        assertThat(items.get(1).rawChannelTitle()).isEqualTo("Uploading Channel");
    }

    @Test
    void activeImportIsEmptyWithoutARunningJob() {
        when(jobRepository.findByPlaylistIdAndStatusOrderByCreatedAtDesc(PLAYLIST_ID, PlaylistImportJobStatus.RUNNING))
                .thenReturn(List.of());

        assertThat(service().findActiveImport(PLAYLIST_ID)).isEmpty();
    }
}
