package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongResolutionServiceTest {

    private static final String YOUTUBE_ID = "dQw4w9WgXcQ";
    private static final Long EXISTING_SONG_ID = 6L;
    private static final Integer RESOLVED_SITELINKS_COUNT = 24;

    @Mock
    private SongMetadataService songMetadataService;
    @Mock
    private SongRepository songRepository;

    private SongResolutionService songResolutionService;

    private SongResolutionService service() {
        return new SongResolutionService(songMetadataService, songRepository);
    }

    private AiResponse successResponse(String verificationStatus) {
        SongMetadataResponse content = new SongMetadataResponse(
                "Never Gonna Give You Up", List.of("Rick Astley"), List.of(), 1987, "abcdef",
                "high", "musicbrainz+discogs+wikidata-lock", "All three sources agree", verificationStatus,
                RESOLVED_SITELINKS_COUNT);
        return new AiResponse(content, "gpt-5.1", 100L, LocalDateTime.now(), "SUCCESS", null, null);
    }

    @Test
    void persistsTheAiVerificationStatusOntoTheNewSong() {
        songResolutionService = service();
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(successResponse("VERIFIED"));
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of());
        when(songRepository.save(org.mockito.ArgumentMatchers.any(Song.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, null);

        assertThat(resolvedSong).isPresent();
        assertThat(resolvedSong.get().getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void persistsTheWikidataSitelinksCountOntoTheNewSong() {
        songResolutionService = service();
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(successResponse("VERIFIED"));
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of());
        when(songRepository.save(org.mockito.ArgumentMatchers.any(Song.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, null);

        assertThat(resolvedSong).isPresent();
        assertThat(resolvedSong.get().getWikidataSitelinksCount()).isEqualTo(RESOLVED_SITELINKS_COUNT);
    }

    @Test
    void reprocessingAnAlreadyKnownYoutubeIdUpdatesTheExistingSongInsteadOfInsertingADuplicate() {
        songResolutionService = service();
        Song existingSong = new Song();
        existingSong.setId(EXISTING_SONG_ID);
        existingSong.setYoutubeId(YOUTUBE_ID);
        existingSong.setVerificationStatus(VerificationStatus.MANUAL_ENTRY);

        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(successResponse("VERIFIED"));
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of(existingSong));
        when(songRepository.save(existingSong)).thenReturn(existingSong);

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, null);

        ArgumentCaptor<Song> savedSongCaptor = ArgumentCaptor.forClass(Song.class);
        verify(songRepository).save(savedSongCaptor.capture());
        assertThat(savedSongCaptor.getValue().getId()).isEqualTo(EXISTING_SONG_ID);
        assertThat(resolvedSong).contains(existingSong);
        assertThat(existingSong.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void persistsFeaturedArtistsAsFeaturedRowsBehindTheMainArtist() {
        songResolutionService = service();
        SongMetadataResponse content = new SongMetadataResponse(
                "Titanium", List.of("David Guetta"), List.of("Sia"), 2011, "abcdef",
                "high", "musicbrainz+discogs+wikidata-lock", "All three sources agree", "VERIFIED",
                RESOLVED_SITELINKS_COUNT);
        AiResponse response = new AiResponse(content, "gpt-5.1", 100L, LocalDateTime.now(), "SUCCESS", null, null);
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(response);
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of());
        when(songRepository.save(org.mockito.ArgumentMatchers.any(Song.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, null);

        assertThat(resolvedSong).isPresent();
        assertThat(resolvedSong.get().getArtists()).hasSize(2);
        assertThat(resolvedSong.get().getArtists().get(0).getRole()).isEqualTo(ArtistRole.MAIN);
        assertThat(resolvedSong.get().getArtists().get(0).getName()).isEqualTo("David Guetta");
        assertThat(resolvedSong.get().getArtists().get(1).getRole()).isEqualTo(ArtistRole.FEATURED);
        assertThat(resolvedSong.get().getArtists().get(1).getName()).isEqualTo("Sia");
    }

    @Test
    void persistsEveryMainArtistAsItsOwnMainRowInOrder() {
        songResolutionService = service();
        SongMetadataResponse content = new SongMetadataResponse(
                "Cold Heart", List.of("Elton John", "Dua Lipa"), List.of("Pnau"), 2021, "abcdef",
                "high", "musicbrainz+discogs+wikidata-lock", "All three sources agree", "VERIFIED",
                RESOLVED_SITELINKS_COUNT);
        AiResponse response = new AiResponse(content, "gpt-5.1", 100L, LocalDateTime.now(), "SUCCESS", null, null);
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(response);
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of());
        when(songRepository.save(org.mockito.ArgumentMatchers.any(Song.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, null);

        assertThat(resolvedSong).isPresent();
        assertThat(resolvedSong.get().getArtists()).hasSize(3);
        assertThat(resolvedSong.get().getArtists().get(0).getRole()).isEqualTo(ArtistRole.MAIN);
        assertThat(resolvedSong.get().getArtists().get(0).getName()).isEqualTo("Elton John");
        assertThat(resolvedSong.get().getArtists().get(0).getDisplayOrder()).isZero();
        assertThat(resolvedSong.get().getArtists().get(1).getRole()).isEqualTo(ArtistRole.MAIN);
        assertThat(resolvedSong.get().getArtists().get(1).getName()).isEqualTo("Dua Lipa");
        assertThat(resolvedSong.get().getArtists().get(1).getDisplayOrder()).isEqualTo(1);
        assertThat(resolvedSong.get().getArtists().get(2).getRole()).isEqualTo(ArtistRole.FEATURED);
        assertThat(resolvedSong.get().getArtists().get(2).getName()).isEqualTo("Pnau");
        assertThat(resolvedSong.get().getArtists().get(2).getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void attributesANewSongToTheGivenUser() {
        songResolutionService = service();
        User addedBy = new User();
        addedBy.setId(9L);
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(successResponse("VERIFIED"));
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of());
        when(songRepository.save(org.mockito.ArgumentMatchers.any(Song.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, addedBy);

        assertThat(resolvedSong).isPresent();
        assertThat(resolvedSong.get().getAddedBy()).isEqualTo(addedBy);
    }

    @Test
    void reprocessingAnAlreadyAttributedSongDoesNotReassignItToTheReprocessingUser() {
        songResolutionService = service();
        User originalAdder = new User();
        originalAdder.setId(1L);
        User reprocessingUser = new User();
        reprocessingUser.setId(2L);
        Song existingSong = new Song();
        existingSong.setId(EXISTING_SONG_ID);
        existingSong.setYoutubeId(YOUTUBE_ID);
        existingSong.setAddedBy(originalAdder);

        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(successResponse("VERIFIED"));
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of(existingSong));
        when(songRepository.save(existingSong)).thenReturn(existingSong);

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, reprocessingUser);

        assertThat(resolvedSong).isPresent();
        assertThat(resolvedSong.get().getAddedBy()).isEqualTo(originalAdder);
    }

    @Test
    void aNonSuccessResponseResolvesToEmptyWithoutTouchingTheRepository() {
        songResolutionService = service();
        AiResponse errorResponse = new AiResponse(null, null, 10L, LocalDateTime.now(), "ERROR", null, null);
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(errorResponse);

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID, null);

        assertThat(resolvedSong).isEmpty();
        verify(songRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
