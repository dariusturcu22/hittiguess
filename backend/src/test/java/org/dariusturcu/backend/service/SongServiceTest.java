package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
