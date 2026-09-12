package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.playlist.Playlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    Optional<Playlist> findPlaylistByInviteCode(String inviteCode);
}
