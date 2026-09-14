package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.JoinPlaylistRequest;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PlaylistMapper playlistMapper;
    @Mock
    private PlaylistMembershipRepository playlistMembershipRepository;
    @Mock
    private PlaylistBanRepository playlistBanRepository;
    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private UserService userService;

    private static final Long PLAYLIST_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String INVITE_CODE = "INVITE1";

    private User currentUser;
    private Playlist playlist;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(USER_ID);
        currentUser.setUsername("account-username");
        currentUser.setImageUrl("https://example.com/account-avatar.png");
        currentUser.setRole(Role.USER);

        playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        playlist.setInviteCode(INVITE_CODE);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(currentUser), null, null)
        );
    }

    private void stubInviteCodeLookup() {
        when(playlistRepository.findPlaylistByInviteCode(INVITE_CODE)).thenReturn(Optional.of(playlist));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void joinPlaylistRejectsABannedUser() {
        stubInviteCodeLookup();
        when(playlistBanRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> userService.joinPlaylist(INVITE_CODE, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void joinPlaylistRejectsAnAlreadyExistingMember() {
        stubInviteCodeLookup();
        when(playlistBanRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID)).thenReturn(false);
        when(playlistMembershipRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> userService.joinPlaylist(INVITE_CODE, null))
                .isInstanceOf(ConflictException.class);

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void joinPlaylistDefaultsTheIdentityToTheAccountsOwnWhenNoRequestBodyIsGiven() {
        stubInviteCodeLookup();
        when(playlistBanRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID)).thenReturn(false);
        when(playlistMembershipRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID)).thenReturn(false);

        userService.joinPlaylist(INVITE_CODE, null);

        assertThat(playlist.getMemberships()).hasSize(1);
        PlaylistMembership membership = playlist.getMemberships().getFirst();
        assertThat(membership.getDisplayName()).isEqualTo("account-username");
        assertThat(membership.getAvatarUrl()).isEqualTo("https://example.com/account-avatar.png");
        assertThat(membership.isCanRead()).isTrue();
        assertThat(membership.isCanWrite()).isTrue();
        assertThat(membership.isCanDelete()).isTrue();
    }

    @Test
    void joinPlaylistUsesTheSubmittedIdentityWhenOverridden() {
        stubInviteCodeLookup();
        when(playlistBanRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID)).thenReturn(false);
        when(playlistMembershipRepository.existsByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID)).thenReturn(false);

        userService.joinPlaylist(INVITE_CODE, new JoinPlaylistRequest("Custom name", "https://example.com/custom.png"));

        PlaylistMembership membership = playlist.getMemberships().getFirst();
        assertThat(membership.getDisplayName()).isEqualTo("Custom name");
        assertThat(membership.getAvatarUrl()).isEqualTo("https://example.com/custom.png");
    }

    @Test
    void leavePlaylistDeletesThePlaylistWhenTheLastMemberLeaves() {
        PlaylistMembership membership = new PlaylistMembership();
        membership.setUser(currentUser);
        playlist.addMembership(membership);
        playlist.setOwner(currentUser);

        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID))
                .thenReturn(Optional.of(membership));

        userService.leavePlaylist(PLAYLIST_ID);

        verify(playlistRepository).delete(playlist);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void leavePlaylistPromotesTheEarliestJoinedRemainingMemberWhenTheOwnerLeaves() {
        User earlierMember = new User();
        earlierMember.setId(99L);
        User laterMember = new User();
        laterMember.setId(98L);

        PlaylistMembership ownerMembership = new PlaylistMembership();
        ownerMembership.setUser(currentUser);
        ownerMembership.setJoinedAt(Instant.parse("2026-01-01T00:00:00Z"));
        PlaylistMembership earlierMembership = new PlaylistMembership();
        earlierMembership.setUser(earlierMember);
        earlierMembership.setJoinedAt(Instant.parse("2026-01-02T00:00:00Z"));
        PlaylistMembership laterMembership = new PlaylistMembership();
        laterMembership.setUser(laterMember);
        laterMembership.setJoinedAt(Instant.parse("2026-01-03T00:00:00Z"));
        playlist.addMembership(ownerMembership);
        playlist.addMembership(earlierMembership);
        playlist.addMembership(laterMembership);
        playlist.setOwner(currentUser);

        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID))
                .thenReturn(Optional.of(ownerMembership));

        userService.leavePlaylist(PLAYLIST_ID);

        assertThat(playlist.getOwner()).isEqualTo(earlierMember);
        assertThat(playlist.getMemberships()).containsExactly(earlierMembership, laterMembership);
        verify(playlistRepository).save(playlist);
        verify(playlistRepository, never()).delete(any());
    }

    @Test
    void leavePlaylistRemovesOnlyTheOwnMembershipAndLeavesOwnershipUnchangedForANonOwner() {
        User otherMember = new User();
        otherMember.setId(99L);
        User owner = new User();
        owner.setId(1L);

        PlaylistMembership memberMembership = new PlaylistMembership();
        memberMembership.setUser(currentUser);
        PlaylistMembership otherMembership = new PlaylistMembership();
        otherMembership.setUser(otherMember);
        playlist.addMembership(memberMembership);
        playlist.addMembership(otherMembership);
        playlist.setOwner(owner);

        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, USER_ID))
                .thenReturn(Optional.of(memberMembership));

        userService.leavePlaylist(PLAYLIST_ID);

        assertThat(playlist.getOwner()).isEqualTo(owner);
        assertThat(playlist.getMemberships()).containsExactly(otherMembership);
        verify(playlistRepository).save(playlist);
        verify(playlistRepository, never()).delete(any());
    }

    @Test
    void deleteUserWithNoSongsOrPlaylistsOnlyRemovesTheAccount() {
        when(songRepository.findByAddedById(USER_ID)).thenReturn(List.of());
        when(playlistMembershipRepository.findByUserId(USER_ID)).thenReturn(List.of());

        userService.deleteUser();

        verify(userRepository).delete(currentUser);
        verify(playlistRepository, never()).delete(any());
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void deleteUserClearsAttributionOnEverySongTheySubmittedInsteadOfBlockingDeletion() {
        Song firstSong = new Song();
        Song secondSong = new Song();
        firstSong.setAddedBy(currentUser);
        secondSong.setAddedBy(currentUser);

        when(songRepository.findByAddedById(USER_ID)).thenReturn(List.of(firstSong, secondSong));
        when(playlistMembershipRepository.findByUserId(USER_ID)).thenReturn(List.of());

        userService.deleteUser();

        assertThat(firstSong.getAddedBy()).isNull();
        assertThat(secondSong.getAddedBy()).isNull();
        verify(songRepository).saveAll(List.of(firstSong, secondSong));
        verify(userRepository).delete(currentUser);
    }

    @Test
    void deleteUserDeletesAPlaylistWhereTheyAreTheLastRemainingMember() {
        PlaylistMembership membership = new PlaylistMembership();
        membership.setUser(currentUser);
        playlist.addMembership(membership);
        playlist.setOwner(currentUser);

        when(songRepository.findByAddedById(USER_ID)).thenReturn(List.of());
        when(playlistMembershipRepository.findByUserId(USER_ID)).thenReturn(List.of(membership));

        userService.deleteUser();

        verify(playlistRepository).delete(playlist);
        verify(playlistRepository, never()).save(any());
        verify(userRepository).delete(currentUser);
    }

    @Test
    void deleteUserPromotesTheEarliestJoinedRemainingMemberWhenTheOwnerIsDeletedFromASharedPlaylist() {
        User remainingMember = new User();
        remainingMember.setId(99L);

        PlaylistMembership ownerMembership = new PlaylistMembership();
        ownerMembership.setUser(currentUser);
        ownerMembership.setJoinedAt(Instant.parse("2026-01-01T00:00:00Z"));
        PlaylistMembership remainingMembership = new PlaylistMembership();
        remainingMembership.setUser(remainingMember);
        remainingMembership.setJoinedAt(Instant.parse("2026-01-02T00:00:00Z"));
        playlist.addMembership(ownerMembership);
        playlist.addMembership(remainingMembership);
        playlist.setOwner(currentUser);

        when(songRepository.findByAddedById(USER_ID)).thenReturn(List.of());
        when(playlistMembershipRepository.findByUserId(USER_ID)).thenReturn(List.of(ownerMembership));

        userService.deleteUser();

        assertThat(playlist.getOwner()).isEqualTo(remainingMember);
        assertThat(playlist.getMemberships()).containsExactly(remainingMembership);
        verify(playlistRepository).save(playlist);
        verify(playlistRepository, never()).delete(any());
        verify(userRepository).delete(currentUser);
    }
}
