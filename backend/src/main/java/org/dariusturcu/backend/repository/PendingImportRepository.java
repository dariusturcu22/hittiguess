package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.PendingImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingImportRepository extends JpaRepository<PendingImport, Long> {

    List<PendingImport> findByYoutubeIdOrderByEnqueuedAtAscIdAsc(String youtubeId);

}
