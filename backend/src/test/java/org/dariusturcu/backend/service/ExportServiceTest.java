package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.PaperSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    private static final Long PLAYLIST_ID = 7L;

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private PlaylistAccessService playlistAccessService;

    private ExportService service() {
        return new ExportService(playlistRepository, playlistAccessService);
    }

    private static Song songWithTitle(String title) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(2000);
        song.setYoutubeId("video-" + title);
        return song;
    }

    private Playlist playlistWithSongs(Song... songs) {
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        for (Song song : songs) {
            playlist.getSongs().add(song);
        }
        return playlist;
    }

    private static byte[] exportWithUser(ExportService exportService, Playlist playlist,
                                         BiFunction<ExportService, Long, byte[]> export) {
        User user = new User();
        user.setId(11L);
        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(user);
            return export.apply(exportService, PLAYLIST_ID);
        }
    }

    @Test
    void infoPdfStartsWithThePdfMagicBytes() {
        Playlist playlist = playlistWithSongs(songWithTitle("One"), songWithTitle("Two"));
        Mockito.when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        byte[] pdf = exportWithUser(service(), playlist,
                (exportService, playlistId) -> exportService.generateInfoPdf(playlistId, PaperSize.A4));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void qrPdfRendersOnEveryPaperSize() {
        Playlist playlist = playlistWithSongs(songWithTitle("One"));
        Mockito.when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        for (PaperSize paperSize : PaperSize.values()) {
            byte[] pdf = exportWithUser(service(), playlist,
                    (exportService, playlistId) -> exportService.generateQrPdf(playlistId, paperSize));
            assertThat(pdf).isNotEmpty();
        }
    }

    @Test
    void combinedPdfRendersOnEveryPaperSize() {
        Playlist playlist = playlistWithSongs(songWithTitle("One"));
        Mockito.when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        for (PaperSize paperSize : PaperSize.values()) {
            byte[] pdf = exportWithUser(service(), playlist,
                    (exportService, playlistId) -> exportService.generateCombinedPdf(playlistId, paperSize));
            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        }
    }

    @Test
    void emptyPlaylistIsRejectedBeforeRendering() {
        Playlist playlist = playlistWithSongs();
        Mockito.when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> exportWithUser(service(), playlist,
                (exportService, playlistId) -> exportService.generateInfoPdf(playlistId, PaperSize.A4)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no songs");
    }

    @Test
    void missingPlaylistRaisesNotFound() {
        Mockito.when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportWithUser(service(), playlistWithSongs(songWithTitle("One")),
                (exportService, playlistId) -> exportService.generateQrPdf(playlistId, PaperSize.LETTER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void readAccessIsCheckedBeforeExport() {
        Playlist playlist = playlistWithSongs(songWithTitle("One"));
        Mockito.when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        exportWithUser(service(), playlist,
                (exportService, playlistId) -> exportService.generateInfoPdf(playlistId, PaperSize.A4));

        verify(playlistAccessService).requireRead(any(Playlist.class), any(User.class));
    }

    @Test
    void readAccessIsCheckedBeforeCombinedExportTheSameAsTheOtherTwoOutputs() {
        Playlist playlist = playlistWithSongs(songWithTitle("One"));
        Mockito.when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        exportWithUser(service(), playlist,
                (exportService, playlistId) -> exportService.generateCombinedPdf(playlistId, PaperSize.A4));

        verify(playlistAccessService).requireRead(any(Playlist.class), any(User.class));
    }
}
