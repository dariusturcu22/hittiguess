package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.SongReport;
import org.dariusturcu.backend.model.song.SongReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SongReportRepository extends JpaRepository<SongReport, Long> {

    boolean existsByReporterIdAndSongId(Long reporterId, Long songId);

    long countBySongIdAndStatus(Long songId, SongReportStatus status);

    List<SongReport> findBySongIdAndStatus(Long songId, SongReportStatus status);

    List<SongReport> findByStatus(SongReportStatus status);
}
