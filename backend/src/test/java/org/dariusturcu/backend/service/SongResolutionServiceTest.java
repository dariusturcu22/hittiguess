package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
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
                "Never Gonna Give You Up", "Rick Astley", 1987, "abcdef",
                "high", "musicbrainz+discogs+wikidata-lock", "All three sources agree", verificationStatus);
        return new AiResponse(content, "gpt-5.1", 100L, LocalDateTime.now(), "SUCCESS", null, null);
    }

    @Test
    void persistsTheAiVerificationStatusOntoTheNewSong() {
        songResolutionService = service();
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(successResponse("VERIFIED"));
        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of());
        when(songRepository.save(org.mockito.ArgumentMatchers.any(Song.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID);

        assertThat(resolvedSong).isPresent();
        assertThat(resolvedSong.get().getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
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

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID);

        ArgumentCaptor<Song> savedSongCaptor = ArgumentCaptor.forClass(Song.class);
        verify(songRepository).save(savedSongCaptor.capture());
        assertThat(savedSongCaptor.getValue().getId()).isEqualTo(EXISTING_SONG_ID);
        assertThat(resolvedSong).contains(existingSong);
        assertThat(existingSong.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void aNonSuccessResponseResolvesToEmptyWithoutTouchingTheRepository() {
        songResolutionService = service();
        AiResponse errorResponse = new AiResponse(null, null, 10L, LocalDateTime.now(), "ERROR", null, null);
        when(songMetadataService.resolveByYoutubeId(YOUTUBE_ID)).thenReturn(errorResponse);

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(YOUTUBE_ID);

        assertThat(resolvedSong).isEmpty();
        verify(songRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
