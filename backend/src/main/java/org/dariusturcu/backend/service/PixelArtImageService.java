package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

/**
 * Enforces the pixel-art image rule: uploads arrive pixelized from the client,
 * and this normalizes them to small PNG bytes for direct database storage.
 * Anything that is not a decodable image, or is larger than the pixel grid,
 * is rejected rather than stored.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PixelArtImageService {

    static final int MAX_IMAGE_DIMENSION_PIXELS = 64;
    static final int MAX_UPLOAD_BYTES = 256_000;
    private static final String PNG_FORMAT = "png";
    private static final int FIRST_IMAGE_INDEX = 0;

    private final PlaylistRepository playlistRepository;
    private final PlaylistAccessService playlistAccessService;
    private final UserRepository userRepository;

    public void storePlaylistCover(Long playlistId, byte[] upload, User currentUser) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        playlistAccessService.requireWrite(playlist, currentUser);
        playlist.setCoverImage(normalizeToPixelPng(upload));
        playlistRepository.save(playlist);
    }

    @Transactional(readOnly = true)
    public byte[] readPlaylistCover(Long playlistId, User currentUser) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not found"));
        playlistAccessService.requireRead(playlist, currentUser);
        byte[] coverImage = playlist.getCoverImage();
        if (coverImage == null || coverImage.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist has no custom cover");
        }
        return coverImage;
    }

    public void storeUserAvatar(Long userId, byte[] upload) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setAvatarImage(normalizeToPixelPng(upload));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public byte[] readUserAvatar(Long userId) {
        return userRepository.findById(userId)
                .map(User::getAvatarImage)
                .filter(image -> image.length > 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User has no custom avatar"));
    }

    public byte[] normalizeToPixelPng(byte[] upload) {
        if (upload == null || upload.length == 0 || upload.length > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image upload is empty or too large");
        }
        BufferedImage decoded = decode(upload);
        BufferedImage normalized = new BufferedImage(
                decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.drawImage(decoded, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(normalized, PNG_FORMAT, output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image could not be stored", failure);
        }
    }

    // The header's declared dimensions are checked before any pixel data is decoded, so a
    // small file declaring a huge canvas is refused without allocating that canvas.
    private BufferedImage decode(byte[] upload) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = imageInput == null ? Collections.emptyIterator() : ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload is not a decodable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                if (reader.getWidth(FIRST_IMAGE_INDEX) > MAX_IMAGE_DIMENSION_PIXELS
                        || reader.getHeight(FIRST_IMAGE_INDEX) > MAX_IMAGE_DIMENSION_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image exceeds the pixel-art size");
                }
                return reader.read(FIRST_IMAGE_INDEX);
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException refusal) {
            throw refusal;
        } catch (IOException | RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload is not a decodable image", failure);
        }
    }
}
