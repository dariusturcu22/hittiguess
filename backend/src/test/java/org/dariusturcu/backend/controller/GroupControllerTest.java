package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.difficulty.DifficultyTier;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.session.GenerateDifficultySetRequest;
import org.dariusturcu.backend.model.session.GeneratedSongPreviewDTO;
import org.dariusturcu.backend.model.session.StartCustomSessionRequest;
import org.dariusturcu.backend.model.session.StartSessionWithSongsRequest;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.service.TurnCredentialsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    private static final Long GROUP_ID = 10L;

    @Mock
    private GroupService groupService;
    @Mock
    private GameSessionService gameSessionService;
    @Mock
    private TurnCredentialsService turnCredentialsService;

    private GroupController controller() {
        return new GroupController(groupService, gameSessionService, turnCredentialsService);
    }

    @Test
    void generateDifficultySetReturnsTheServicesPreview() {
        GenerateDifficultySetRequest request = new GenerateDifficultySetRequest(DifficultyTier.EASY, 8);
        List<GeneratedSongPreviewDTO> previews =
                List.of(new GeneratedSongPreviewDTO(11L, "Song 11", List.of("Artist 11"), 1999));
        when(gameSessionService.generateDifficultySet(GROUP_ID, request)).thenReturn(previews);

        ResponseEntity<List<GeneratedSongPreviewDTO>> response = controller().generateDifficultySet(GROUP_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(previews);
    }

    @Test
    void startSessionWithSongsReturnsTheStartedGroup() {
        StartSessionWithSongsRequest request = new StartSessionWithSongsRequest(List.of(11L, 12L, 13L));
        GroupDetailDTO started = mock(GroupDetailDTO.class);
        when(gameSessionService.startSessionWithSongs(GROUP_ID, request)).thenReturn(started);

        ResponseEntity<GroupDetailDTO> response = controller().startSessionWithSongs(GROUP_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(started);
    }

    @Test
    void startCustomSessionReturnsTheStartedGroup() {
        StartCustomSessionRequest request = new StartCustomSessionRequest(21L, null);
        GroupDetailDTO started = mock(GroupDetailDTO.class);
        when(gameSessionService.startCustomSession(GROUP_ID, request)).thenReturn(started);

        ResponseEntity<GroupDetailDTO> response = controller().startCustomSession(GROUP_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(started);
    }
}
