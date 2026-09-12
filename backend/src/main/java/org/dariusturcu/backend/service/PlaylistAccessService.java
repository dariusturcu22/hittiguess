package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaylistAccessService {

    private static final String NOT_A_MEMBER_MESSAGE = "You are not a member of this playlist";

    private final PlaylistMembershipRepository playlistMembershipRepository;

    private PlaylistMembership findMembership(Playlist playlist, User user) {
        return playlistMembershipRepository.findByPlaylistIdAndUserId(playlist.getId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException(NOT_A_MEMBER_MESSAGE));
    }

    public void requireOwner(Playlist playlist, User user) {
        if (!playlist.isOwnedBy(user)) {
            throw new AccessDeniedException("Only the playlist owner can perform this action");
        }
    }

    public void requireRead(Playlist playlist, User user) {
        if (playlist.isOwnedBy(user)) {
            return;
        }
        if (!findMembership(playlist, user).isCanRead()) {
            throw new AccessDeniedException("You don't have read access to this playlist");
        }
    }

    public void requireWrite(Playlist playlist, User user) {
        if (playlist.isOwnedBy(user)) {
            return;
        }
        if (!findMembership(playlist, user).isCanWrite()) {
            throw new AccessDeniedException("You don't have write access to this playlist");
        }
    }

    public void requireDelete(Playlist playlist, User user) {
        if (playlist.isOwnedBy(user)) {
            return;
        }
        if (!findMembership(playlist, user).isCanDelete()) {
            throw new AccessDeniedException("You don't have delete access to this playlist");
        }
    }
}
