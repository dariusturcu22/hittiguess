package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.playlist.PlaylistImportJobDTO;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItemDTO;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItemStatus;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobStatus;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.service.PlaylistImportJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistImportJobControllerTest {

    @Mock
    private PlaylistImportJobService playlistImportJobService;

    private PlaylistImportJobController controller() {
        return new PlaylistImportJobController(playlistImportJobService);
    }

    @Test
    void startImportReturnsAcceptedWithTheJobId() {
        when(playlistImportJobService.startImport(eq(7L), any(StartPlaylistImportRequest.class)))
                .thenReturn("job-1");

        ResponseEntity<Map<String, String>> response = controller().startImport(
                7L, new StartPlaylistImportRequest(List.of("video-1"), null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(Map.of("importJobId", "job-1"));
    }

    @Test
    void activeImportReturnsTheRunningJob() {
        PlaylistImportJobDTO job = new PlaylistImportJobDTO(
                "job-1", 7L, PlaylistImportJobStatus.RUNNING, null, null,
                List.of(new PlaylistImportJobItemDTO(
                        "video-1", PlaylistImportJobItemStatus.PENDING, null, null, null, null, null, null, null)));
        when(playlistImportJobService.findActiveImport(7L)).thenReturn(Optional.of(job));

        ResponseEntity<PlaylistImportJobDTO> response = controller().activeImport(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(job);
    }

    @Test
    void activeImportReturnsNotFoundWithoutARunningJob() {
        when(playlistImportJobService.findActiveImport(7L)).thenReturn(Optional.empty());

        ResponseEntity<PlaylistImportJobDTO> response = controller().activeImport(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
