package org.dariusturcu.backend.model.song;

import java.util.List;

/**
 * One entry in the admin review queue for a song carrying an open report or a confirmation.
 * Exposes the raw signals behind the song's queue position rather than a single opaque score:
 * the open reports and whether they converge on a year, and the confirmation count. The admin
 * makes the actual call from these.
 */
public record AdminReviewItemDTO(
        Long songId,
        String songTitle,
        String artistName,
        int releaseYear,
        VerificationStatus verificationStatus,
        ReviewPriorityTier priorityTier,
        long openReportCount,
        boolean reportsConverge,
        Integer convergingYear,
        long convergingReportCount,
        long confirmationCount,
        List<ReportSummaryDTO> openReports) {
}
