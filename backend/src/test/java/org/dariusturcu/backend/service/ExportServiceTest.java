package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.util.PaperSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    private static final int MAX_SONGS_PER_EXPORT = 500;
    private static final int SONGS_OVER_THE_EXPORT_LIMIT = MAX_SONGS_PER_EXPORT + 1;
    private static final Long PLAYLIST_ID = 1L;

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private PlaylistAccessService playlistAccessService;

    @InjectMocks
    private ExportService exportService;

    private User currentUser;
    private Playlist playlist;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRole(Role.USER);

        playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        playlist.setSongs(new ArrayList<>());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(currentUser), null, null));

        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Song songWithGradient() {
        Song song = new Song();
        song.setTitle("Some Title");
        song.setYoutubeId("dQw4w9WgXcQ");
        song.setGradientColor1("abcdef");
        song.setGradientColor2("123456");
        return song;
    }

    @Test
    void generateInfoPdfRejectsAMissingPlaylist() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.A4))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateInfoPdfRejectsACallerWithoutReadAccess() {
        doThrow(new AccessDeniedException("You don't have read access to this playlist"))
                .when(playlistAccessService).requireRead(playlist, currentUser);

        assertThatThrownBy(() -> exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.A4))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void generateInfoPdfRejectsAnEmptyPlaylist() {
        assertThatThrownBy(() -> exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.A4))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no songs");
    }

    @Test
    void generateInfoPdfRejectsAPlaylistOverTheExportLimit() {
        List<Song> tooManySongs = new ArrayList<>();
        for (int songIndex = 0; songIndex < SONGS_OVER_THE_EXPORT_LIMIT; songIndex++) {
            tooManySongs.add(songWithGradient());
        }
        playlist.setSongs(tooManySongs);

        assertThatThrownBy(() -> exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.A4))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(String.valueOf(MAX_SONGS_PER_EXPORT));
    }

    @Test
    void generateInfoPdfReturnsNonEmptyPdfBytesForAValidPlaylist() {
        playlist.setSongs(List.of(songWithGradient()));

        byte[] pdfBytes = exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.A4);

        assertThat(pdfBytes).isNotEmpty();
    }

    @Test
    void generateQrPdfReturnsNonEmptyPdfBytesForAValidPlaylist() {
        playlist.setSongs(List.of(songWithGradient()));

        byte[] pdfBytes = exportService.generateQrPdf(PLAYLIST_ID, PaperSize.A4);

        assertThat(pdfBytes).isNotEmpty();
    }
}
