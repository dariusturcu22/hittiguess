package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private SongRepository songRepository;
    @Mock
    private PlaylistMapper playlistMapper;
    @Mock
    private SongMapper songMapper;

    @InjectMocks
    private PlaylistService playlistService;

    private static final Long PLAYLIST_ID = 1L;
    private static final Long SONG_ID = 2L;

    private User currentUser;
    private Playlist playlist;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(10L);
        currentUser.setUsername("current-user");
        currentUser.setRole(Role.USER);

        playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        Set<User> members = new HashSet<>();
        members.add(currentUser);
        playlist.setUsers(members);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(currentUser), null, null)
        );

        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Song songWithStatus(VerificationStatus status) {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setPlaylist(playlist);
        song.setVerificationStatus(status);
        return song;
    }

    private UpdateSongRequest anyUpdateRequest() {
        return new UpdateSongRequest("Artist", "Title", 2000, "dQw4w9WgXcQ", "abcdef", "abcdef", null);
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"UNVERIFIED", "MANUAL_ENTRY"})
    void updateSongAcceptsAnEditForEditableStatuses(VerificationStatus status) {
        Song song = songWithStatus(status);
        UpdateSongRequest request = anyUpdateRequest();
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(songMapper.updateEntity(song, request)).thenReturn(song);

        playlistService.updateSong(PLAYLIST_ID, SONG_ID, request);

        verify(songMapper).updateEntity(song, request);
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"VERIFIED", "NEEDS_REVIEW"})
    void updateSongRejectsAnEditForLockedStatuses(VerificationStatus status) {
        Song song = songWithStatus(status);
        UpdateSongRequest request = anyUpdateRequest();
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));

        assertThatThrownBy(() -> playlistService.updateSong(PLAYLIST_ID, SONG_ID, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(songMapper, never()).updateEntity(any(), any());
    }
}
