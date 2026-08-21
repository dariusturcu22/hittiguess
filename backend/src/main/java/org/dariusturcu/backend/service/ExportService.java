package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.CardGenerator;
import org.dariusturcu.backend.util.QRGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportService {
    private final PlaylistRepository playlistRepository;

    public byte[] generatePdf(Long playlistId) {
        List<Song> songs = getValidatedSongs(playlistId);
        try {
            return buildPdf(songs, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private byte[] buildPdf(List<Song> songs, boolean isQr) throws IOException {
        PDDocument document = new PDDocument();

        List<List<Song>> chunks = new ArrayList<>();
        int pageSize = 12;
        for (int i = 0; i < songs.size(); i += pageSize) {
            chunks.add(songs.subList(i, Math.min(i + pageSize, songs.size())));
        }

        if (isQr) Collections.reverse(chunks);

        for (List<Song> chunk : chunks) {
            BufferedImage image = isQr
                    ? QRGenerator.generateQRPage(chunk)
                    : CardGenerator.generateInfoPage(chunk);
            addImagePage(document, image);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.save(outputStream);
        document.close();

        return outputStream.toByteArray();
    }

    private void addImagePage(PDDocument document, BufferedImage image) throws IOException {
        PDPage page = new PDPage(new PDRectangle(
                image.getWidth() * 72f / 300f,
                image.getHeight() * 72f / 300f
        ));
        document.addPage(page);

        PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.drawImage(pdImage, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
        contentStream.close();
    }

    public byte[] generateInfoPdf(Long playlistId) {
        List<Song> songs = getValidatedSongs(playlistId);
        try {
            return buildPdf(songs, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    public byte[] generateQrPdf(Long playlistId) {
        List<Song> songs = getValidatedSongs(playlistId);
        try {
            return buildPdf(songs, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private List<Song> getValidatedSongs(Long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));

        boolean hasAccess = playlist.getUsers().stream()
                .anyMatch(user -> user.getId().equals(SecurityUtils.getCurrentUserId()));

        if (!hasAccess) {
            throw new AccessDeniedException("You are not a member of this playlist");
        }

        List<Song> songs = playlist.getSongs();

        if (songs.isEmpty()) {
            throw new RuntimeException("Playlist has no songs to generate PDF for");
        }
        return songs;
    }
}
