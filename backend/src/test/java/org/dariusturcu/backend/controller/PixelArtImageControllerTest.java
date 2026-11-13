package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.PixelArtImageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PixelArtImageControllerTest {

    private static final long USER_ID = 42L;
    private static final long PLAYLIST_ID = 100L;
    private static final int VALID_FILE_SIZE = 1024;
    private static final int OVERSIZED_FILE_SIZE = 300_000;

    @Mock
    private PixelArtImageService pixelArtImageService;

    private User currentUser;

    @BeforeEach
    void setUpSecurityContext() {
        currentUser = new User();
        currentUser.setId(USER_ID);
        currentUser.setUsername("testuser");
        currentUser.setRole(Role.USER);
        UserPrincipal principal = new UserPrincipal(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private PixelArtImageController controller() {
        return new PixelArtImageController(pixelArtImageService);
    }

    @Test
    void uploadOwnAvatarRejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "avatar", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        assertThatThrownBy(() -> controller().uploadOwnAvatar(emptyFile))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadOwnAvatarRejectsDisallowedMimeType() {
        MockMultipartFile htmlFile = new MockMultipartFile(
                "avatar", "avatar.html", MediaType.TEXT_HTML_VALUE, new byte[VALID_FILE_SIZE]);

        assertThatThrownBy(() -> controller().uploadOwnAvatar(htmlFile))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadOwnAvatarRejectsOversizedUpload() {
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "avatar", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[OVERSIZED_FILE_SIZE]);

        assertThatThrownBy(() -> controller().uploadOwnAvatar(oversizedFile))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadOwnAvatarAcceptsValidImage() throws IOException {
        MockMultipartFile validFile = new MockMultipartFile(
                "avatar", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[VALID_FILE_SIZE]);

        ResponseEntity<Void> response = controller().uploadOwnAvatar(validFile);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pixelArtImageService).storeUserAvatar(eq(USER_ID), any(byte[].class));
    }

    @Test
    void uploadPlaylistCoverRejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "cover", "cover.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        assertThatThrownBy(() -> controller().uploadPlaylistCover(PLAYLIST_ID, emptyFile))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadPlaylistCoverRejectsDisallowedMimeType() {
        MockMultipartFile svgFile = new MockMultipartFile(
                "cover", "cover.svg", "image/svg+xml", new byte[VALID_FILE_SIZE]);

        assertThatThrownBy(() -> controller().uploadPlaylistCover(PLAYLIST_ID, svgFile))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadPlaylistCoverAcceptsValidImage() throws IOException {
        MockMultipartFile validFile = new MockMultipartFile(
                "cover", "cover.jpeg", MediaType.IMAGE_JPEG_VALUE, new byte[VALID_FILE_SIZE]);

        ResponseEntity<Void> response = controller().uploadPlaylistCover(PLAYLIST_ID, validFile);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pixelArtImageService).storePlaylistCover(eq(PLAYLIST_ID), any(byte[].class), any(User.class));
    }
}
