package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.song.ExpandPlaylistLinkRequest;
import org.dariusturcu.backend.service.BulkImportService;
import org.dariusturcu.backend.service.PlaylistExpansionService;
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
class BulkImportControllerTest {

    @Mock
    private BulkImportService bulkImportService;
    @Mock
    private PlaylistExpansionService playlistExpansionService;

    private BulkImportController controller() {
        return new BulkImportController(bulkImportService, playlistExpansionService);
    }

    @Test
    void expandPlaylistReturnsTheExpandedVideoIds() {
        List<String> videoIds = List.of("video-1", "video-2");
        when(playlistExpansionService.expandPlaylist("https://youtube.com/playlist?list=abc"))
                .thenReturn(videoIds);

        ResponseEntity<List<String>> response = controller().expandPlaylist(
                new ExpandPlaylistLinkRequest("https://youtube.com/playlist?list=abc"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(videoIds);
    }
}
