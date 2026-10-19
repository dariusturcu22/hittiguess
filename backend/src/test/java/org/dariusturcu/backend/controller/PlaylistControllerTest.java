package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.playlist.PlaylistInvitePreviewDTO;
import org.dariusturcu.backend.service.PlaylistImportService;
import org.dariusturcu.backend.service.PlaylistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistControllerTest {

    private static final String INVITE_CODE = "invite-code-abc123";
    private static final String UNKNOWN_INVITE_CODE = "unknown-invite-code";

    @Mock
    private PlaylistService playlistService;
    @Mock
    private PlaylistImportService playlistImportService;

    private PlaylistController controller() {
        return new PlaylistController(playlistService, playlistImportService);
    }

    @Test
    void getInvitePreviewReturnsTheServicesPreviewForAValidCode() {
        PlaylistInvitePreviewDTO preview = new PlaylistInvitePreviewDTO("Midnight Radio", "cba6f7", 32, List.of());
        when(playlistService.getInvitePreview(INVITE_CODE)).thenReturn(preview);

        ResponseEntity<PlaylistInvitePreviewDTO> response = controller().getInvitePreview(INVITE_CODE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(preview);
    }

    @Test
    void getInvitePreviewPropagatesANotFoundForAnUnknownCode() {
        when(playlistService.getInvitePreview(UNKNOWN_INVITE_CODE))
                .thenThrow(new ResourceNotFoundException("Invite code {" + UNKNOWN_INVITE_CODE + "} not found"));

        assertThatThrownBy(() -> controller().getInvitePreview(UNKNOWN_INVITE_CODE))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
