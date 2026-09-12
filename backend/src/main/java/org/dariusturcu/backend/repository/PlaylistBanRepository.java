package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.playlist.PlaylistBan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistBanRepository extends JpaRepository<PlaylistBan, Long> {
    boolean existsByPlaylistIdAndUserId(Long playlistId, Long userId);
}
