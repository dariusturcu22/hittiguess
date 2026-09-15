package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.playlist.SavedPlaylist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPlaylistRepository extends JpaRepository<SavedPlaylist, Long> {
    Optional<SavedPlaylist> findByUserIdAndPlaylistId(Long userId, Long playlistId);

    boolean existsByUserIdAndPlaylistId(Long userId, Long playlistId);

    List<SavedPlaylist> findByUserId(Long userId);
}
