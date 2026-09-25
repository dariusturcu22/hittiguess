package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.playlist.Playlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    @Modifying
    @Query(value = "DELETE FROM group_playlists WHERE playlist_id = :playlistId", nativeQuery = true)
    void deleteGroupPlaylistLinks(@Param("playlistId") Long playlistId);
    Optional<Playlist> findPlaylistByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    List<Playlist> findByIsPublicTrue();
}
