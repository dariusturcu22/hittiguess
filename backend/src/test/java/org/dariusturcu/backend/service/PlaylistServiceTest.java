package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.playlist.UpdateMembershipGrantsRequest;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    @Mock
    private PlaylistAccessService playlistAccessService;
    @Mock
    private PlaylistMembershipRepository playlistMembershipRepository;
    @Mock
    private PlaylistBanRepository playlistBanRepository;

    @InjectMocks
    private PlaylistService playlistService;

    private static final Long PLAYLIST_ID = 1L;
    private static final Long SONG_ID = 2L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    private User currentUser;
    private User owner;
    private Playlist playlist;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(OWNER_ID);
        currentUser.setUsername("current-user");
        currentUser.setRole(Role.USER);

        owner = currentUser;

        playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        playlist.setOwner(owner);
        playlist.setMemberships(new ArrayList<>());

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
        song.getPlaylists().add(playlist);
        song.setVerificationStatus(status);
        return song;
    }

    private UpdateSongRequest anyUpdateRequest() {
        return new UpdateSongRequest("Artist", "Title", 2000, "dQw4w9WgXcQ", "abcdef", "abcdef", null);
    }

    private PlaylistMembership memberMembership() {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("member");
        member.setRole(Role.USER);

        PlaylistMembership membership = new PlaylistMembership();
        membership.setPlaylist(playlist);
        membership.setUser(member);
        membership.setCanRead(true);
        membership.setCanWrite(true);
        membership.setCanDelete(true);
        membership.setDisplayName("member");
        membership.setJoinedAt(Instant.now());
        return membership;
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"UNVERIFIED", "MANUAL_ENTRY"})
    void updateSongAcceptsAnEditForEditableStatuses(VerificationStatus status) {
        Song song = songWithStatus(status);
        UpdateSongRequest request = anyUpdateRequest();
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(songRepository.existsByIdAndPlaylistsId(SONG_ID, PLAYLIST_ID)).thenReturn(true);
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
        when(songRepository.existsByIdAndPlaylistsId(SONG_ID, PLAYLIST_ID)).thenReturn(true);

        assertThatThrownBy(() -> playlistService.updateSong(PLAYLIST_ID, SONG_ID, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(songMapper, never()).updateEntity(any(), any());
    }

    @Test
    void updateSongRejectsAMemberWithoutWriteAccess() {
        UpdateSongRequest request = anyUpdateRequest();
        doThrow(new AccessDeniedException("You don't have write access to this playlist"))
                .when(playlistAccessService).requireWrite(playlist, currentUser);

        assertThatThrownBy(() -> playlistService.updateSong(PLAYLIST_ID, SONG_ID, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(songMapper, never()).updateEntity(any(), any());
    }

    @Test
    void deleteSongRejectsAMemberWithoutDeleteAccess() {
        doThrow(new AccessDeniedException("You don't have delete access to this playlist"))
                .when(playlistAccessService).requireDelete(playlist, currentUser);

        assertThatThrownBy(() -> playlistService.deleteSong(PLAYLIST_ID, SONG_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void updatePlaylistRejectsANonOwnerMember() {
        UpdatePlaylistRequest request = new UpdatePlaylistRequest("New name", null);
        doThrow(new AccessDeniedException("Only the playlist owner can perform this action"))
                .when(playlistAccessService).requireOwner(playlist, currentUser);

        assertThatThrownBy(() -> playlistService.updatePlaylist(PLAYLIST_ID, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(playlistMapper, never()).updateEntity(any(), any());
    }

    @Test
    void updateMemberGrantsRejectsANonOwnerMember() {
        UpdateMembershipGrantsRequest request = new UpdateMembershipGrantsRequest(false, null, null);
        doThrow(new AccessDeniedException("Only the playlist owner can perform this action"))
                .when(playlistAccessService).requireOwner(playlist, currentUser);

        assertThatThrownBy(() -> playlistService.updateMemberGrants(PLAYLIST_ID, MEMBER_ID, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(playlistMembershipRepository, never()).save(any());
    }

    @Test
    void updateMemberGrantsRejectsChangingTheOwnersOwnGrants() {
        UpdateMembershipGrantsRequest request = new UpdateMembershipGrantsRequest(false, null, null);

        assertThatThrownBy(() -> playlistService.updateMemberGrants(PLAYLIST_ID, OWNER_ID, request))
                .isInstanceOf(ConflictException.class);

        verify(playlistMembershipRepository, never()).save(any());
    }

    @Test
    void updateMemberGrantsChangesOnlyTheProvidedFields() {
        PlaylistMembership membership = memberMembership();
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership));
        when(playlistMembershipRepository.save(membership)).thenReturn(membership);

        playlistService.updateMemberGrants(PLAYLIST_ID, MEMBER_ID, new UpdateMembershipGrantsRequest(false, null, null));

        assertThat(membership.isCanRead()).isFalse();
        assertThat(membership.isCanWrite()).isTrue();
        assertThat(membership.isCanDelete()).isTrue();
    }

    @Test
    void transferOwnershipRejectsANonOwnerMember() {
        doThrow(new AccessDeniedException("Only the playlist owner can perform this action"))
                .when(playlistAccessService).requireOwner(playlist, currentUser);

        assertThatThrownBy(() -> playlistService.transferOwnership(PLAYLIST_ID, MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void transferOwnershipRejectsTransferringToTheCurrentOwner() {
        assertThatThrownBy(() -> playlistService.transferOwnership(PLAYLIST_ID, OWNER_ID))
                .isInstanceOf(ConflictException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void transferOwnershipRejectsANonMemberTarget() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> playlistService.transferOwnership(PLAYLIST_ID, MEMBER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void transferOwnershipMovesTheOwnerToTheChosenMemberAndKeepsThePreviousOwnerAsAMember() {
        PlaylistMembership membership = memberMembership();
        playlist.getMemberships().add(membership);
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership));
        when(playlistRepository.save(playlist)).thenReturn(playlist);
        when(playlistMapper.toDetailDTO(playlist)).thenReturn(null);

        playlistService.transferOwnership(PLAYLIST_ID, MEMBER_ID);

        assertThat(playlist.getOwner()).isEqualTo(membership.getUser());
        assertThat(playlist.getMemberships()).contains(membership);
        verify(playlistRepository).save(playlist);
    }

    @Test
    void kickMemberRejectsANonOwnerMember() {
        doThrow(new AccessDeniedException("Only the playlist owner can perform this action"))
                .when(playlistAccessService).requireOwner(playlist, currentUser);

        assertThatThrownBy(() -> playlistService.kickMember(PLAYLIST_ID, MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void kickMemberRejectsKickingTheOwner() {
        assertThatThrownBy(() -> playlistService.kickMember(PLAYLIST_ID, OWNER_ID))
                .isInstanceOf(ConflictException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void kickMemberRemovesTheMembershipWithoutCreatingABan() {
        PlaylistMembership membership = memberMembership();
        playlist.getMemberships().add(membership);
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership));

        playlistService.kickMember(PLAYLIST_ID, MEMBER_ID);

        assertThat(playlist.getMemberships()).doesNotContain(membership);
        verify(playlistBanRepository, never()).save(any());
        verify(playlistRepository).save(playlist);
    }

    @Test
    void banMemberRejectsANonOwnerMember() {
        doThrow(new AccessDeniedException("Only the playlist owner can perform this action"))
                .when(playlistAccessService).requireOwner(playlist, currentUser);

        assertThatThrownBy(() -> playlistService.banMember(PLAYLIST_ID, MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(playlistBanRepository, never()).save(any());
    }

    @Test
    void banMemberRejectsBanningTheOwner() {
        assertThatThrownBy(() -> playlistService.banMember(PLAYLIST_ID, OWNER_ID))
                .isInstanceOf(ConflictException.class);

        verify(playlistBanRepository, never()).save(any());
    }

    @Test
    void banMemberRemovesTheMembershipAndCreatesABan() {
        PlaylistMembership membership = memberMembership();
        playlist.getMemberships().add(membership);
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership));
        when(playlistBanRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID)).thenReturn(false);

        playlistService.banMember(PLAYLIST_ID, MEMBER_ID);

        assertThat(playlist.getMemberships()).doesNotContain(membership);
        verify(playlistBanRepository).save(any());
        verify(playlistRepository).save(playlist);
    }

    @Test
    void getSongRejectsASongThatDoesNotBelongToThePlaylist() {
        Song song = songWithStatus(VerificationStatus.UNVERIFIED);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(songRepository.existsByIdAndPlaylistsId(SONG_ID, PLAYLIST_ID)).thenReturn(false);

        assertThatThrownBy(() -> playlistService.getSong(PLAYLIST_ID, SONG_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSongOnlyUnlinksWhenTheSongBelongsToAnotherPlaylist() {
        Song song = songWithStatus(VerificationStatus.UNVERIFIED);
        Playlist otherPlaylist = new Playlist();
        otherPlaylist.setId(99L);
        song.getPlaylists().add(otherPlaylist);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(songRepository.existsByIdAndPlaylistsId(SONG_ID, PLAYLIST_ID)).thenReturn(true);

        playlistService.deleteSong(PLAYLIST_ID, SONG_ID);

        assertThat(song.getPlaylists()).containsExactly(otherPlaylist);
        verify(songRepository, never()).delete(any());
    }

    @Test
    void deleteSongOnlyUnlinksAndNeverDeletesEvenWhenItsTheOnlyPlaylistItBelongsTo() {
        Song song = songWithStatus(VerificationStatus.UNVERIFIED);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(song));
        when(songRepository.existsByIdAndPlaylistsId(SONG_ID, PLAYLIST_ID)).thenReturn(true);

        playlistService.deleteSong(PLAYLIST_ID, SONG_ID);

        assertThat(song.getPlaylists()).isEmpty();
        verify(songRepository, never()).delete(any());
    }
}
