package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.song.GroundTruthSongDTO;
import org.dariusturcu.backend.model.song.GroundTruthSongsResponse;
import org.dariusturcu.backend.service.GroundTruthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroundTruthControllerTest {

    @Mock
    private GroundTruthService groundTruthService;

    private GroundTruthController controller() {
        return new GroundTruthController(groundTruthService);
    }

    @Test
    void verifiedSongsReturnsTheServicesPage() {
        List<GroundTruthSongDTO> triples = List.of(new GroundTruthSongDTO("Queen", "Bohemian Rhapsody", 1975));
        when(groundTruthService.verifiedSongs(any(Pageable.class)))
                .thenReturn(new PageImpl<>(triples));

        ResponseEntity<GroundTruthSongsResponse> response = controller().verifiedSongs(PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new GroundTruthSongsResponse(triples, 0, 1, 1));
        verify(groundTruthService).verifiedSongs(PageRequest.of(0, 20));
    }
}
