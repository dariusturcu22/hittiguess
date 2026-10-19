package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.playlist.PlaylistMembership;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaylistMembershipRepository extends JpaRepository<PlaylistMembership, Long> {
    Optional<PlaylistMembership> findByPlaylistIdAndUserId(Long playlistId, Long userId);

    List<PlaylistMembership> findByUserId(Long userId);

    boolean existsByPlaylistIdAndUserId(Long playlistId, Long userId);
}
