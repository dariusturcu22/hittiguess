package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.Country;
import org.dariusturcu.backend.model.song.RecommendedSongsDTO;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    @Mock
    private SongRepository songRepository;
    @Mock
    private SongMapper songMapper;

    @InjectMocks
    private SongService songService;

    private static final String YOUTUBE_VIDEO_ID = "dQw4w9WgXcQ";
    private static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=" + YOUTUBE_VIDEO_ID;
    private static final String KEYWORD = "bohemian rhapsody";

    @Test
    void searchByKeywordQueriesTitleAndArtistNotYoutubeId() {
        Song matchingSong = new Song();
        when(songRepository.searchByTitleOrArtistKeyword(KEYWORD)).thenReturn(List.of(matchingSong));

        List<SongDTO> results = songService.searchCatalog(KEYWORD);

        assertThat(results).hasSize(1);
        verify(songMapper).toDTO(matchingSong);
        verify(songRepository, never()).findByYoutubeId(any());
    }

    @Test
    void searchByYoutubeWatchUrlQueriesTheExtractedVideoIdNotAsAKeyword() {
        Song matchingSong = new Song();
        when(songRepository.findByYoutubeId(YOUTUBE_VIDEO_ID)).thenReturn(List.of(matchingSong));

        List<SongDTO> results = songService.searchCatalog(YOUTUBE_WATCH_URL);

        assertThat(results).hasSize(1);
        verify(songMapper).toDTO(matchingSong);
        verify(songRepository, never()).searchByTitleOrArtistKeyword(any());
    }

    @Test
    void searchByBareVideoIdQueriesByYoutubeId() {
        when(songRepository.findByYoutubeId(YOUTUBE_VIDEO_ID)).thenReturn(List.of());

        songService.searchCatalog(YOUTUBE_VIDEO_ID);

        verify(songRepository).findByYoutubeId(YOUTUBE_VIDEO_ID);
        verify(songRepository, never()).searchByTitleOrArtistKeyword(any());
    }

    @Test
    void rejectsABlankQuery() {
        assertThatThrownBy(() -> songService.searchCatalog("   "))
                .isInstanceOf(ResponseStatusException.class);

        verify(songRepository, never()).findByYoutubeId(any());
        verify(songRepository, never()).searchByTitleOrArtistKeyword(any());
    }

    @Test
    void recommendationsReturnVerifiedSongsWithMoreFlag() {
        Song firstSong = new Song();
        Song secondSong = new Song();
        Song overflowSong = new Song();
        when(songRepository.findByVerificationStatus(eq(VerificationStatus.VERIFIED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstSong, secondSong, overflowSong)));
        SongDTO firstDTO = new SongDTO(1L, List.of(), "First", 1999, "video-1", "cba6f7", null,
                Country.NONE, VerificationStatus.VERIFIED, "high", false, null);
        SongDTO secondDTO = new SongDTO(2L, List.of(), "Second", 2000, "video-2", "cba6f7", null,
                Country.NONE, VerificationStatus.VERIFIED, "high", false, null);
        when(songMapper.toDTO(firstSong)).thenReturn(firstDTO);
        when(songMapper.toDTO(secondSong)).thenReturn(secondDTO);

        RecommendedSongsDTO result = songService.recommendSongs(0, 2);

        assertThat(result.songs()).containsExactly(firstDTO, secondDTO);
        assertThat(result.hasMore()).isTrue();
        verify(songMapper, never()).toDTO(overflowSong);
    }

    @Test
    void recommendationsReportNoMoreOnTheLastPage() {
        when(songRepository.findByVerificationStatus(eq(VerificationStatus.VERIFIED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RecommendedSongsDTO result = songService.recommendSongs(3, 2);

        assertThat(result.songs()).isEmpty();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void recommendationsRejectABadPageOrSize() {
        assertThatThrownBy(() -> songService.recommendSongs(-1, 2))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> songService.recommendSongs(0, 0))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> songService.recommendSongs(0, 51))
                .isInstanceOf(ResponseStatusException.class);

        verify(songRepository, never()).findByVerificationStatus(any(), any(Pageable.class));
    }
}
