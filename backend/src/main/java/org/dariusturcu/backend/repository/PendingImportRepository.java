package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PendingImportRepository extends JpaRepository<PendingImport, Long> {

    List<PendingImport> findByStatusOrderByEnqueuedAtAsc(PendingImportStatus status, Limit limit);

    long countByStatus(PendingImportStatus status);

    long countByStatusAndProcessedAtAfter(PendingImportStatus status, Instant threshold);

    List<PendingImport> findTop7ByOrderByEnqueuedAtDesc();

    @Query("""
            select pendingImport.youtubeId
            from PendingImport pendingImport
            where pendingImport.youtubeId in :youtubeIds
              and pendingImport.status in :activeStatuses
            """)
    List<String> findYoutubeIdsAlreadyQueued(
            @Param("youtubeIds") Collection<String> youtubeIds,
            @Param("activeStatuses") Collection<PendingImportStatus> activeStatuses);
}
