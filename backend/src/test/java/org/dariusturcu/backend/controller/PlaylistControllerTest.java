package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaylistControllerTest {

    @Mock
    private PlaylistService playlistService;

    private MockMvc mockMvc;

    private static final Long PLAYLIST_ID = 1L;
    private static final Long SONG_ID = 2L;
    private static final Long MEMBER_ID = 3L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlaylistController(playlistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPlaylistReturnsNotFoundForAMissingPlaylist() throws Exception {
        when(playlistService.getPlaylist(PLAYLIST_ID))
                .thenThrow(new ResourceNotFoundException("Playlist not found"));

        mockMvc.perform(get("/api/playlists/{playlistId}", PLAYLIST_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPlaylistRejectsACallerWithoutReadAccess() throws Exception {
        when(playlistService.getPlaylist(PLAYLIST_ID))
                .thenThrow(new AccessDeniedException("You don't have read access to this playlist"));

        mockMvc.perform(get("/api/playlists/{playlistId}", PLAYLIST_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePlaylistRejectsAnInvalidColor() throws Exception {
        mockMvc.perform(patch("/api/playlists/{playlistId}", PLAYLIST_ID)
                        .contentType("application/json")
                        .content("""
                                {"name":"New name","color":"not-a-hex-color"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSongRejectsAPayloadMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/playlists/{playlistId}/songs", PLAYLIST_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSongRejectsAnInvalidYoutubeId() throws Exception {
        mockMvc.perform(post("/api/playlists/{playlistId}/songs", PLAYLIST_ID)
                        .contentType("application/json")
                        .content("""
                                {"artist":"Artist","title":"Title","releaseYear":2000,
                                 "youtubeId":"too-short","gradientColor1":"abcdef","gradientColor2":"abcdef"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSongRejectsAnEditToAnAlreadyVerifiedSong() throws Exception {
        when(playlistService.updateSong(eq(PLAYLIST_ID), eq(SONG_ID), any()))
                .thenThrow(new AccessDeniedException("This song has been verified and can no longer be edited directly"));

        mockMvc.perform(patch("/api/playlists/{playlistId}/songs/{songId}", PLAYLIST_ID, SONG_ID)
                        .contentType("application/json")
                        .content("""
                                {"artist":"Artist","title":"Title","releaseYear":2000,
                                 "youtubeId":"dQw4w9WgXcQ","gradientColor1":"abcdef","gradientColor2":"abcdef"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSongUnlinksTheSongAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/playlists/{playlistId}/songs/{songId}", PLAYLIST_ID, SONG_ID))
                .andExpect(status().isNoContent());

        verify(playlistService).deleteSong(PLAYLIST_ID, SONG_ID);
    }

    @Test
    void updateMemberGrantsRejectsChangingTheOwnersOwnGrants() throws Exception {
        when(playlistService.updateMemberGrants(eq(PLAYLIST_ID), eq(MEMBER_ID), any()))
                .thenThrow(new ConflictException("The playlist owner can't have their own grants changed"));

        mockMvc.perform(patch("/api/playlists/{playlistId}/members/{userId}", PLAYLIST_ID, MEMBER_ID)
                        .contentType("application/json")
                        .content("{\"canRead\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void kickMemberDelegatesToTheServiceAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/playlists/{playlistId}/members/{userId}", PLAYLIST_ID, MEMBER_ID))
                .andExpect(status().isNoContent());

        verify(playlistService).kickMember(PLAYLIST_ID, MEMBER_ID);
    }

    @Test
    void banMemberDelegatesToTheServiceAndReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/playlists/{playlistId}/members/{userId}/ban", PLAYLIST_ID, MEMBER_ID))
                .andExpect(status().isNoContent());

        verify(playlistService).banMember(PLAYLIST_ID, MEMBER_ID);
    }
}
