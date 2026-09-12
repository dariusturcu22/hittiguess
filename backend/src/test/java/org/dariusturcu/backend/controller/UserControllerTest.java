package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    private static final Long PLAYLIST_ID = 1L;
    private static final String INVITE_CODE = "INVITE1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateUserRejectsAnInvalidEmail() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType("application/json")
                        .content("""
                                {"username":"validname","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUserRejectsATooShortUsername() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType("application/json")
                        .content("""
                                {"username":"ab"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUserDelegatesToTheServiceAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser();
    }

    @Test
    void createPlaylistReturnsCreatedStatus() throws Exception {
        mockMvc.perform(post("/api/users/me/playlists"))
                .andExpect(status().isCreated());
    }

    @Test
    void joinPlaylistDefaultsTheRequestBodyToNullWhenNoneIsSent() throws Exception {
        when(userService.joinPlaylist(INVITE_CODE, null)).thenReturn(
                new PlaylistSummaryDTO(PLAYLIST_ID, "Playlist name", "abcdef", 0));

        mockMvc.perform(post("/api/users/me/playlists/{code}", INVITE_CODE))
                .andExpect(status().isOk());

        verify(userService).joinPlaylist(INVITE_CODE, null);
    }

    @Test
    void joinPlaylistRejectsABannedUser() throws Exception {
        when(userService.joinPlaylist(any(), any())).thenThrow(new AccessDeniedException("You have been banned from this playlist"));

        mockMvc.perform(post("/api/users/me/playlists/{code}", INVITE_CODE)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leavePlaylistDelegatesToTheServiceAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/me/playlists/{playlistId}", PLAYLIST_ID))
                .andExpect(status().isNoContent());

        verify(userService).leavePlaylist(PLAYLIST_ID);
    }
}
