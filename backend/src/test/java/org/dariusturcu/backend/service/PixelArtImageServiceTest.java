package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PixelArtImageServiceTest {

    private static final int PIXEL_GRID_SIZE = 32;
    private static final int OVERSIZED_GRID_SIZE = 128;
    private static final int HUGE_DECLARED_DIMENSION = 60_000;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int IHDR_DATA_LENGTH = 13;
    private static final byte PNG_BIT_DEPTH = 8;
    private static final byte PNG_COLOR_TYPE_TRUECOLOR_WITH_ALPHA = 6;
    private static final byte PNG_DEFLATE_COMPRESSION_METHOD = 0;
    private static final byte PNG_ADAPTIVE_FILTER_METHOD = 0;
    private static final byte PNG_NO_INTERLACE_METHOD = 0;

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private PlaylistAccessService playlistAccessService;
    @Mock
    private UserRepository userRepository;

    private PixelArtImageService service() {
        return new PixelArtImageService(playlistRepository, playlistAccessService, userRepository);
    }

    private static byte[] pixelPng(int dimension) throws Exception {
        BufferedImage image = new BufferedImage(dimension, dimension, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    // A PNG whose header declares the given canvas but carries no pixel data at all, so
    // only its declared dimensions can be read from it.
    private static byte[] headerOnlyPng(int declaredDimension) throws Exception {
        ByteBuffer headerData = ByteBuffer.allocate(IHDR_DATA_LENGTH)
                .putInt(declaredDimension)
                .putInt(declaredDimension)
                .put(PNG_BIT_DEPTH)
                .put(PNG_COLOR_TYPE_TRUECOLOR_WITH_ALPHA)
                .put(PNG_DEFLATE_COMPRESSION_METHOD)
                .put(PNG_ADAPTIVE_FILTER_METHOD)
                .put(PNG_NO_INTERLACE_METHOD);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream pngOutput = new DataOutputStream(output)) {
            pngOutput.write(PNG_SIGNATURE);
            writePngChunk(pngOutput, "IHDR", headerData.array());
            writePngChunk(pngOutput, "IEND", new byte[0]);
            return output.toByteArray();
        }
    }

    private static void writePngChunk(DataOutputStream pngOutput, String chunkType, byte[] chunkData) throws Exception {
        byte[] typeBytes = chunkType.getBytes(StandardCharsets.US_ASCII);
        CRC32 checksum = new CRC32();
        checksum.update(typeBytes);
        checksum.update(chunkData);
        pngOutput.writeInt(chunkData.length);
        pngOutput.write(typeBytes);
        pngOutput.write(chunkData);
        pngOutput.writeInt((int) checksum.getValue());
    }

    private static User userWithId(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void smallImageNormalizesToPngBytes() throws Exception {
        byte[] normalized = service().normalizeToPixelPng(pixelPng(PIXEL_GRID_SIZE));

        assertThat(normalized).isNotEmpty();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(normalized));
        assertThat(decoded.getWidth()).isEqualTo(PIXEL_GRID_SIZE);
        assertThat(decoded.getHeight()).isEqualTo(PIXEL_GRID_SIZE);
    }

    @Test
    void oversizedImageIsRejected() throws Exception {
        PixelArtImageService imageService = service();

        assertThatThrownBy(() -> imageService.normalizeToPixelPng(pixelPng(OVERSIZED_GRID_SIZE)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pixel-art size");
    }

    @Test
    void anImageDeclaringHugeDimensionsIsRefusedFromItsHeaderBeforeDecoding() throws Exception {
        PixelArtImageService imageService = service();
        byte[] upload = headerOnlyPng(HUGE_DECLARED_DIMENSION);

        assertThatThrownBy(() -> imageService.normalizeToPixelPng(upload))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pixel-art size");
    }

    @Test
    void nonImageUploadIsRejected() {
        PixelArtImageService imageService = service();

        assertThatThrownBy(() -> imageService.normalizeToPixelPng("not an image".getBytes()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void emptyUploadIsRejected() {
        PixelArtImageService imageService = service();

        assertThatThrownBy(() -> imageService.normalizeToPixelPng(new byte[0]))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void storePlaylistCoverPersistsNormalizedBytes() throws Exception {
        Playlist playlist = new Playlist();
        playlist.setId(7L);
        when(playlistRepository.findById(7L)).thenReturn(Optional.of(playlist));

        service().storePlaylistCover(7L, pixelPng(PIXEL_GRID_SIZE), userWithId(11L));

        ArgumentCaptor<Playlist> savedPlaylist = ArgumentCaptor.forClass(Playlist.class);
        verify(playlistRepository).save(savedPlaylist.capture());
        assertThat(savedPlaylist.getValue().getCoverImage()).isNotEmpty();
    }

    @Test
    void readPlaylistCoverReportsMissingCover() {
        Playlist playlist = new Playlist();
        playlist.setId(7L);
        when(playlistRepository.findById(7L)).thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> service().readPlaylistCover(7L, userWithId(11L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void readPlaylistCoverRefusesAUserWhoCantReadThePlaylist() throws Exception {
        Playlist privatePlaylist = new Playlist();
        privatePlaylist.setId(7L);
        privatePlaylist.setCoverImage(pixelPng(PIXEL_GRID_SIZE));
        User outsider = userWithId(12L);
        when(playlistRepository.findById(7L)).thenReturn(Optional.of(privatePlaylist));
        doThrow(new AccessDeniedException("You are not a member of this playlist"))
                .when(playlistAccessService).requireRead(privatePlaylist, outsider);

        assertThatThrownBy(() -> service().readPlaylistCover(7L, outsider))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void readPlaylistCoverReturnsTheCoverToAReader() throws Exception {
        Playlist playlist = new Playlist();
        playlist.setId(7L);
        byte[] cover = pixelPng(PIXEL_GRID_SIZE);
        playlist.setCoverImage(cover);
        when(playlistRepository.findById(7L)).thenReturn(Optional.of(playlist));

        assertThat(service().readPlaylistCover(7L, userWithId(11L))).isEqualTo(cover);
    }

    @Test
    void storeUserAvatarPersistsNormalizedBytes() throws Exception {
        User user = userWithId(11L);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));

        service().storeUserAvatar(11L, pixelPng(PIXEL_GRID_SIZE));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getAvatarImage()).isNotEmpty();
    }

    @Test
    void readUserAvatarReportsMissingAvatar() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(userWithId(11L)));

        assertThatThrownBy(() -> service().readUserAvatar(11L))
                .isInstanceOf(ResponseStatusException.class);
    }
}
