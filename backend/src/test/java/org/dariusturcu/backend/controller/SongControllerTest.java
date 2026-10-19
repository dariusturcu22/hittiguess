package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.song.RecommendedSongsDTO;
import org.dariusturcu.backend.service.SongService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongControllerTest {

    @Mock
    private SongService songService;

    private SongController controller() {
        return new SongController(songService);
    }

    @Test
    void recommendSongsReturnsTheServicesPage() {
        RecommendedSongsDTO page = new RecommendedSongsDTO(List.of(), false);
        when(songService.recommendSongs(1, 20)).thenReturn(page);

        ResponseEntity<RecommendedSongsDTO> response = controller().recommendSongs(1, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(page);
    }
}
