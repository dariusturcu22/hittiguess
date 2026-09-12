package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistAccessServiceTest {

    @Mock
    private PlaylistMembershipRepository playlistMembershipRepository;

    @InjectMocks
    private PlaylistAccessService playlistAccessService;

    private static final Long PLAYLIST_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    private User owner;
    private User member;
    private Playlist playlist;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(OWNER_ID);
        owner.setRole(Role.USER);

        member = new User();
        member.setId(MEMBER_ID);
        member.setRole(Role.USER);

        playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        playlist.setOwner(owner);
    }

    private PlaylistMembership membershipWithGrants(boolean canRead, boolean canWrite, boolean canDelete) {
        PlaylistMembership membership = new PlaylistMembership();
        membership.setPlaylist(playlist);
        membership.setUser(member);
        membership.setCanRead(canRead);
        membership.setCanWrite(canWrite);
        membership.setCanDelete(canDelete);
        return membership;
    }

    @Test
    void ownerAlwaysPassesEveryCheckWithNoMembershipRowNeeded() {
        assertThatCode(() -> playlistAccessService.requireRead(playlist, owner)).doesNotThrowAnyException();
        assertThatCode(() -> playlistAccessService.requireWrite(playlist, owner)).doesNotThrowAnyException();
        assertThatCode(() -> playlistAccessService.requireDelete(playlist, owner)).doesNotThrowAnyException();
        assertThatCode(() -> playlistAccessService.requireOwner(playlist, owner)).doesNotThrowAnyException();
    }

    @Test
    void requireOwnerRejectsANonOwnerMemberRegardlessOfGrants() {
        assertThatThrownBy(() -> playlistAccessService.requireOwner(playlist, member))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireReadRejectsAMemberWithReadRevoked() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membershipWithGrants(false, true, true)));

        assertThatThrownBy(() -> playlistAccessService.requireRead(playlist, member))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireReadAllowsAMemberWithReadGranted() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membershipWithGrants(true, false, false)));

        assertThatCode(() -> playlistAccessService.requireRead(playlist, member)).doesNotThrowAnyException();
    }

    @Test
    void requireWriteRejectsAMemberWithWriteRevoked() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membershipWithGrants(true, false, true)));

        assertThatThrownBy(() -> playlistAccessService.requireWrite(playlist, member))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireWriteAllowsAMemberWithWriteGrantedEvenIfReadIsRevoked() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membershipWithGrants(false, true, false)));

        assertThatCode(() -> playlistAccessService.requireWrite(playlist, member)).doesNotThrowAnyException();
    }

    @Test
    void requireDeleteRejectsAMemberWithDeleteRevoked() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membershipWithGrants(true, true, false)));

        assertThatThrownBy(() -> playlistAccessService.requireDelete(playlist, member))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireDeleteAllowsAMemberWithDeleteGrantedEvenIfWriteIsRevoked() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.of(membershipWithGrants(true, false, true)));

        assertThatCode(() -> playlistAccessService.requireDelete(playlist, member)).doesNotThrowAnyException();
    }

    @Test
    void everyCheckRejectsAUserWithNoMembershipRowAtAll() {
        when(playlistMembershipRepository.findByPlaylistIdAndUserId(PLAYLIST_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> playlistAccessService.requireRead(playlist, member))
                .isInstanceOf(AccessDeniedException.class);
    }
}
