package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.playlist.PlaylistImportJob;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaylistImportJobRepository extends JpaRepository<PlaylistImportJob, String> {

    List<PlaylistImportJob> findByPlaylistIdAndStatusOrderByCreatedAtDesc(Long playlistId, PlaylistImportJobStatus status);

    Optional<PlaylistImportJob> findFirstByPlaylistIdOrderByCreatedAtDesc(Long playlistId);
}
