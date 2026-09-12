package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.service.ExportService;
import org.dariusturcu.backend.util.PaperSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    @Mock
    private ExportService exportService;

    private MockMvc mockMvc;

    private static final Long PLAYLIST_ID = 1L;
    private static final byte[] FAKE_PDF_BYTES = new byte[]{1, 2, 3};

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExportController(exportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exportPlaylistInfoReturnsAPdfAttachmentWithTheDefaultPaperSize() throws Exception {
        when(exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.A4)).thenReturn(FAKE_PDF_BYTES);

        mockMvc.perform(get("/api/playlists/{playlistId}/export/info", PLAYLIST_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("info-1.pdf")))
                .andExpect(content().bytes(FAKE_PDF_BYTES));
    }

    @Test
    void exportPlaylistInfoHonorsAnExplicitPaperSize() throws Exception {
        when(exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.LETTER)).thenReturn(FAKE_PDF_BYTES);

        mockMvc.perform(get("/api/playlists/{playlistId}/export/info", PLAYLIST_ID).param("paperSize", "LETTER"))
                .andExpect(status().isOk());

        verify(exportService).generateInfoPdf(PLAYLIST_ID, PaperSize.LETTER);
    }

    @Test
    void exportPlaylistInfoReturnsNotFoundForAMissingPlaylist() throws Exception {
        when(exportService.generateInfoPdf(PLAYLIST_ID, PaperSize.A4))
                .thenThrow(new ResourceNotFoundException("Playlist not found"));

        mockMvc.perform(get("/api/playlists/{playlistId}/export/info", PLAYLIST_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportPlaylistQrReturnsAPdfAttachment() throws Exception {
        when(exportService.generateQrPdf(PLAYLIST_ID, PaperSize.A4)).thenReturn(FAKE_PDF_BYTES);

        mockMvc.perform(get("/api/playlists/{playlistId}/export/qr", PLAYLIST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("qr-1.pdf")));
    }
}
