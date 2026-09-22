package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.playlist.PlaylistImportJobItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaylistImportJobItemRepository extends JpaRepository<PlaylistImportJobItem, Long> {

    List<PlaylistImportJobItem> findByJobIdOrderByIdAsc(String jobId);
}
