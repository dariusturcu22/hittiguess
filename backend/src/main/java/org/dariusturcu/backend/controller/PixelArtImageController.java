package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.service.PixelArtImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Pixel-art images", description = "Pixelized playlist covers and profile pictures stored directly in the database")
public class PixelArtImageController {

    private static final String WEBP_MEDIA_TYPE = "image/webp";
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            WEBP_MEDIA_TYPE
    );

    private final PixelArtImageService pixelArtImageService;

    public static void validateImageUpload(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty() || imageFile.getSize() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image upload is empty");
        }
        String contentType = imageFile.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image upload must be a PNG, JPEG, or WebP image");
        }
        if (imageFile.getSize() > PixelArtImageService.MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image upload exceeds the 256KB size limit");
        }
    }

    @Operation(summary = "Upload a pixelized playlist cover, replacing any previous one")
    @PutMapping(value = "/playlists/{playlistId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadPlaylistCover(
            @PathVariable Long playlistId,
            @RequestParam("cover") MultipartFile cover) throws IOException {
        validateImageUpload(cover);
        User currentUser = SecurityUtils.getCurrentUser();
        pixelArtImageService.storePlaylistCover(playlistId, cover.getBytes(), currentUser);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Read a playlist's custom cover, if it has one, must be able to read the playlist")
    @GetMapping(value = "/playlists/{playlistId}/cover", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> readPlaylistCover(@PathVariable Long playlistId) {
        return ResponseEntity.ok(pixelArtImageService.readPlaylistCover(playlistId, SecurityUtils.getCurrentUser()));
    }

    @Operation(summary = "Upload the current user's pixelized profile picture")
    @PutMapping(value = "/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadOwnAvatar(@RequestParam("avatar") MultipartFile avatar) throws IOException {
        validateImageUpload(avatar);
        User currentUser = SecurityUtils.getCurrentUser();
        pixelArtImageService.storeUserAvatar(currentUser.getId(), avatar.getBytes());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Read a user's custom avatar, if they have one")
    @GetMapping(value = "/users/{userId}/avatar", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> readUserAvatar(@PathVariable Long userId) {
        return ResponseEntity.ok(pixelArtImageService.readUserAvatar(userId));
    }
}
