package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.PlaylistImportException;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.BulkImportResultDTO;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.websocket.BulkImportProgressEvent;
import org.dariusturcu.backend.websocket.BulkImportProgressOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceTest {

    // YoutubeLinkParser only accepts exactly-11-character ids, so every submitted id
    // below is shaped like a real one rather than a short descriptive placeholder.
    private static final String SUBMITTING_USERNAME = "submitter";
    private static final String EXTRA_VIDEO_ID = "extraId0001";
    private static final String EXPANDED_VIDEO_ID = "expandedId1";
    private static final String PLAIN_VIDEO_ID = "videoId0001";
    private static final String ALREADY_KNOWN_VIDEO_ID = "alreadyKnwn";
    private static final String RESOLVES_VIDEO_ID = "resolvesId1";
    private static final String UNRESOLVED_VIDEO_ID = "unresolved1";

    @Mock
    private YoutubeIdLookupService youtubeIdLookupService;
    @Mock
    private SongResolutionService songResolutionService;
    @Mock
    private CatalogSeedingService catalogSeedingService;
    @Mock
    private PlaylistExpansionService playlistExpansionService;
    @Mock
    private PlaylistImportService playlistImportService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private BulkImportService bulkImportService;

    @BeforeEach
    void setUp() {
        MetadataPriorityCoordinator metadataPriorityCoordinator = new MetadataPriorityCoordinator();
        bulkImportService = new BulkImportService(youtubeIdLookupService, songResolutionService,
                catalogSeedingService, metadataPriorityCoordinator, playlistExpansionService, playlistImportService,
                applicationEventPublisher);

        User submittingUser = new User();
        submittingUser.setUsername(SUBMITTING_USERNAME);
        submittingUser.setRole(Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(submittingUser), null, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expandsAndMergesThePlaylistLinkWithTheSubmittedIdsBeforeLookup() {
        when(playlistExpansionService.expandAndMerge(eq("playlist-link"), eq(List.of(EXTRA_VIDEO_ID))))
                .thenReturn(List.of(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(List.of(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID)))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID)));
        when(songResolutionService.resolveAndPersist(EXPANDED_VIDEO_ID)).thenReturn(Optional.of(mock(Song.class)));
        when(songResolutionService.resolveAndPersist(EXTRA_VIDEO_ID)).thenReturn(Optional.of(mock(Song.class)));

        BulkImportResultDTO result = bulkImportService.importImmediately(
                new BulkImportRequest("playlist-link", List.of(EXTRA_VIDEO_ID), null));

        assertThat(result.resolvedYoutubeIds()).containsExactlyInAnyOrder(EXPANDED_VIDEO_ID, EXTRA_VIDEO_ID);
    }

    @Test
    void resolvedIdsAreReEnqueuedForPatientReprocessing() {
        when(playlistExpansionService.expandAndMerge(null, List.of(PLAIN_VIDEO_ID)))
                .thenReturn(List.of(PLAIN_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(List.of(PLAIN_VIDEO_ID)))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(PLAIN_VIDEO_ID)));
        when(songResolutionService.resolveAndPersist(PLAIN_VIDEO_ID)).thenReturn(Optional.of(mock(Song.class)));

        bulkImportService.importImmediately(new BulkImportRequest(null, List.of(PLAIN_VIDEO_ID), null));

        verify(catalogSeedingService).reEnqueueForPatientReprocessing(PLAIN_VIDEO_ID);
    }

    @Test
    void publishesOneProgressEventPerSubmittedIdWithTheRightOutcome() {
        List<String> submittedIds = List.of(ALREADY_KNOWN_VIDEO_ID, RESOLVES_VIDEO_ID, UNRESOLVED_VIDEO_ID);
        when(playlistExpansionService.expandAndMerge(null, submittedIds)).thenReturn(submittedIds);
        when(youtubeIdLookupService.partitionKnownAndUnknown(submittedIds)).thenReturn(new YoutubeIdLookupResult(
                Set.of(ALREADY_KNOWN_VIDEO_ID), Set.of(RESOLVES_VIDEO_ID, UNRESOLVED_VIDEO_ID)));
        when(songResolutionService.resolveAndPersist(RESOLVES_VIDEO_ID)).thenReturn(Optional.of(mock(Song.class)));
        when(songResolutionService.resolveAndPersist(UNRESOLVED_VIDEO_ID)).thenReturn(Optional.empty());

        bulkImportService.importImmediately(new BulkImportRequest(null, submittedIds, null));

        ArgumentCaptor<BulkImportProgressEvent> eventCaptor = ArgumentCaptor.forClass(BulkImportProgressEvent.class);
        verify(applicationEventPublisher, times(3)).publishEvent(eventCaptor.capture());

        List<BulkImportProgressEvent> publishedEvents = eventCaptor.getAllValues();
        assertThat(publishedEvents).allSatisfy(event -> assertThat(event.username()).isEqualTo(SUBMITTING_USERNAME));
        assertThat(publishedEvents).extracting(BulkImportProgressEvent::youtubeId, BulkImportProgressEvent::outcome)
                .containsExactlyInAnyOrder(
                        tuple(ALREADY_KNOWN_VIDEO_ID, BulkImportProgressOutcome.ALREADY_KNOWN),
                        tuple(RESOLVES_VIDEO_ID, BulkImportProgressOutcome.RESOLVED),
                        tuple(UNRESOLVED_VIDEO_ID, BulkImportProgressOutcome.UNRESOLVED));
    }

    @Test
    void aPlaylistExpansionFailurePropagatesRatherThanImportingSilently() {
        when(playlistExpansionService.expandAndMerge(eq("bad-link"), eq(List.of())))
                .thenThrow(new PlaylistImportException("Not a valid playlist link"));

        assertThatThrownBy(() -> bulkImportService.importImmediately(new BulkImportRequest("bad-link", List.of(), null)))
                .isInstanceOf(PlaylistImportException.class);
    }

    @Test
    void withNoTargetPlaylistIdNothingIsLinkedIntoAnyPlaylist() {
        when(playlistExpansionService.expandAndMerge(null, List.of(PLAIN_VIDEO_ID))).thenReturn(List.of(PLAIN_VIDEO_ID));
        when(youtubeIdLookupService.partitionKnownAndUnknown(List.of(PLAIN_VIDEO_ID)))
                .thenReturn(new YoutubeIdLookupResult(Set.of(), Set.of(PLAIN_VIDEO_ID)));
        when(songResolutionService.resolveAndPersist(PLAIN_VIDEO_ID)).thenReturn(Optional.of(mock(Song.class)));

        bulkImportService.importImmediately(new BulkImportRequest(null, List.of(PLAIN_VIDEO_ID), null));

        verifyNoInteractions(playlistImportService);
    }

    @Test
    void withATargetPlaylistIdBothKnownAndNewlyResolvedSongsAreLinkedIntoIt() {
        Long targetPlaylistId = 42L;
        List<String> submittedIds = List.of(ALREADY_KNOWN_VIDEO_ID, RESOLVES_VIDEO_ID);
        Song alreadyKnownSong = mock(Song.class);
        Song resolvedSong = mock(Song.class);

        when(playlistExpansionService.expandAndMerge(null, submittedIds)).thenReturn(submittedIds);
        when(youtubeIdLookupService.partitionKnownAndUnknown(submittedIds)).thenReturn(
                new YoutubeIdLookupResult(Set.of(ALREADY_KNOWN_VIDEO_ID), Set.of(RESOLVES_VIDEO_ID)));
        when(youtubeIdLookupService.resolveCanonicalSongs(Set.of(ALREADY_KNOWN_VIDEO_ID)))
                .thenReturn(List.of(alreadyKnownSong));
        when(songResolutionService.resolveAndPersist(RESOLVES_VIDEO_ID)).thenReturn(Optional.of(resolvedSong));

        bulkImportService.importImmediately(new BulkImportRequest(null, submittedIds, targetPlaylistId));

        ArgumentCaptor<List<Song>> linkedSongsCaptor = ArgumentCaptor.forClass(List.class);
        verify(playlistImportService).addResolvedSongs(eq(targetPlaylistId), linkedSongsCaptor.capture());
        assertThat(linkedSongsCaptor.getValue()).containsExactlyInAnyOrder(alreadyKnownSong, resolvedSong);
    }
}
