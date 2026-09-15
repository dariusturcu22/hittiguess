package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.song.AdminReviewItemDTO;
import org.dariusturcu.backend.model.song.ReportSummaryDTO;
import org.dariusturcu.backend.model.song.ReviewPriorityTier;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongConfirmation;
import org.dariusturcu.backend.model.song.SongReport;
import org.dariusturcu.backend.model.song.SongReportStatus;
import org.dariusturcu.backend.model.song.SubmitReportRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.SongConfirmationRepository;
import org.dariusturcu.backend.repository.SongReportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Community reports and confirmations against songs, and the admin review queue over them.
 * Resolution is manual: an admin decides every case and nothing here changes a song's
 * verification status on its own except the explicit uphold action, which never overwrites a
 * locked song's year, matching PlaylistService's editable-status rule.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SongReportService {

    private static final Logger logger = LoggerFactory.getLogger(SongReportService.class);

    // Two or more open reports agreeing on the same suggested year converge; a single report,
    // or reports disagreeing on the year, do not.
    private static final long CONVERGENCE_MINIMUM_REPORTS = 2;

    // Once a song accumulates this many open reports it is flagged for admin review; recorded
    // as a log line here because story 34's real abuse-visibility pipeline is not built yet.
    private static final long REVIEW_FLAG_REPORT_THRESHOLD = 3;

    // A song's year can only be edited while it sits in one of these statuses, the same rule
    // PlaylistService enforces. Upholding a report against a locked song surfaces it for admin
    // judgment without mutating the year.
    private static final Set<VerificationStatus> EDITABLE_VERIFICATION_STATUSES =
            Set.of(VerificationStatus.UNVERIFIED, VerificationStatus.MANUAL_ENTRY);

    private static final Set<VerificationStatus> CONFIRMABLE_VERIFICATION_STATUSES =
            Set.of(VerificationStatus.NEEDS_REVIEW, VerificationStatus.MANUAL_ENTRY);

    private final SongReportRepository songReportRepository;
    private final SongConfirmationRepository songConfirmationRepository;
    private final SongRepository songRepository;
    private final AbuseVisibilityEvents abuseVisibilityEvents;

    public void submitReport(Long songId, SubmitReportRequest request) {
        User reporter = SecurityUtils.getCurrentUser();
        Song song = findSong(songId);

        if (songReportRepository.existsByReporterIdAndSongId(reporter.getId(), songId)) {
            throw new ConflictException("This song has already been reported by this user");
        }

        SongReport report = new SongReport();
        report.setReporter(reporter);
        report.setSong(song);
        report.setMessage(request.message());
        report.setSuggestedCorrectYear(request.suggestedCorrectYear());
        report.setSources(request.sources());
        report.setStatus(SongReportStatus.OPEN);
        report.setCreatedAt(Instant.now());
        songReportRepository.save(report);

        abuseVisibilityEvents.recordReportSubmitted(reporter.getId(), songId);

        long openReportCount = songReportRepository.countBySongIdAndStatus(songId, SongReportStatus.OPEN);
        if (openReportCount >= REVIEW_FLAG_REPORT_THRESHOLD) {
            logger.info("song_flagged_for_review song_id={} open_report_count={}", songId, openReportCount);
        }
    }

    public void submitConfirmation(Long songId) {
        User user = SecurityUtils.getCurrentUser();
        Song song = findSong(songId);

        if (!CONFIRMABLE_VERIFICATION_STATUSES.contains(song.getVerificationStatus())) {
            throw new ConflictException("This song cannot be confirmed at its current verification status");
        }

        if (songConfirmationRepository.existsByUserIdAndSongId(user.getId(), songId)) {
            throw new ConflictException("This song has already been confirmed by this user");
        }

        SongConfirmation confirmation = new SongConfirmation();
        confirmation.setUser(user);
        confirmation.setSong(song);
        confirmation.setCreatedAt(Instant.now());
        songConfirmationRepository.save(confirmation);
    }

    @Transactional(readOnly = true)
    public List<AdminReviewItemDTO> reviewQueue() {
        Map<Long, AdminReviewItemDTO> itemsBySongId = new LinkedHashMap<>();

        List<SongReport> openReports = songReportRepository.findByStatus(SongReportStatus.OPEN);
        Map<Song, List<SongReport>> reportsBySong = openReports.stream()
                .collect(Collectors.groupingBy(SongReport::getSong));

        for (Map.Entry<Song, List<SongReport>> entry : reportsBySong.entrySet()) {
            Song song = entry.getKey();
            List<SongReport> reports = entry.getValue();
            long confirmationCount = songConfirmationRepository.countBySongId(song.getId());
            itemsBySongId.put(song.getId(), buildReportedItem(song, reports, confirmationCount));
        }

        addUnreportedConfirmedSongs(itemsBySongId);

        List<AdminReviewItemDTO> queue = new ArrayList<>(itemsBySongId.values());
        queue.sort(reviewQueueOrdering());
        return queue;
    }

    public void upholdReports(Long songId) {
        Song song = findSong(songId);
        List<SongReport> openReports = songReportRepository.findBySongIdAndStatus(songId, SongReportStatus.OPEN);
        if (openReports.isEmpty()) {
            throw new ConflictException("This song has no open reports to uphold");
        }

        for (SongReport report : openReports) {
            report.setStatus(SongReportStatus.UPHELD);
        }

        if (EDITABLE_VERIFICATION_STATUSES.contains(song.getVerificationStatus())) {
            song.setVerificationStatus(VerificationStatus.MANUAL_ENTRY);
        }
    }

    public void dismissReports(Long songId) {
        List<SongReport> openReports = songReportRepository.findBySongIdAndStatus(songId, SongReportStatus.OPEN);
        if (openReports.isEmpty()) {
            throw new ConflictException("This song has no open reports to dismiss");
        }

        for (SongReport report : openReports) {
            report.setStatus(SongReportStatus.DISMISSED);
        }
    }

    private Song findSong(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, songId));
    }

    private AdminReviewItemDTO buildReportedItem(Song song, List<SongReport> reports, long confirmationCount) {
        Integer convergingYear = findConvergingYear(reports);
        boolean reportsConverge = convergingYear != null;
        ReviewPriorityTier tier = reportsConverge
                ? ReviewPriorityTier.CONVERGING_REPORTS
                : ReviewPriorityTier.REPORTED_NO_CONVERGENCE;

        List<ReportSummaryDTO> reportSummaries = reports.stream()
                .map(this::toReportSummary)
                .toList();

        return new AdminReviewItemDTO(
                song.getId(),
                song.getTitle(),
                song.getReleaseYear(),
                song.getVerificationStatus(),
                tier,
                reports.size(),
                reportsConverge,
                convergingYear,
                confirmationCount,
                reportSummaries);
    }

    private void addUnreportedConfirmedSongs(Map<Long, AdminReviewItemDTO> itemsBySongId) {
        List<SongConfirmation> confirmations = songConfirmationRepository.findAll();
        Map<Song, List<SongConfirmation>> confirmationsBySong = confirmations.stream()
                .collect(Collectors.groupingBy(SongConfirmation::getSong));

        for (Map.Entry<Song, List<SongConfirmation>> entry : confirmationsBySong.entrySet()) {
            Song song = entry.getKey();
            if (itemsBySongId.containsKey(song.getId())) {
                continue;
            }
            if (!CONFIRMABLE_VERIFICATION_STATUSES.contains(song.getVerificationStatus())) {
                continue;
            }

            long confirmationCount = entry.getValue().size();
            ReviewPriorityTier tier = confirmationCount > 0
                    ? ReviewPriorityTier.CONFIRMED_UNREPORTED
                    : ReviewPriorityTier.UNCONFIRMED_UNREPORTED;

            itemsBySongId.put(song.getId(), new AdminReviewItemDTO(
                    song.getId(),
                    song.getTitle(),
                    song.getReleaseYear(),
                    song.getVerificationStatus(),
                    tier,
                    0,
                    false,
                    null,
                    confirmationCount,
                    List.of()));
        }
    }

    private Integer findConvergingYear(List<SongReport> reports) {
        Map<Integer, Long> countsByYear = reports.stream()
                .map(SongReport::getSuggestedCorrectYear)
                .filter(year -> year != null)
                .collect(Collectors.groupingBy(year -> year, Collectors.counting()));

        return countsByYear.entrySet().stream()
                .filter(yearCount -> yearCount.getValue() >= CONVERGENCE_MINIMUM_REPORTS)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private ReportSummaryDTO toReportSummary(SongReport report) {
        return new ReportSummaryDTO(
                report.getId(),
                report.getReporter().getId(),
                report.getMessage(),
                report.getSuggestedCorrectYear(),
                report.getSources(),
                report.getCreatedAt());
    }

    // Within a tier, a card with more converging reports, then more open reports, then more
    // confirmations, then a lower verification status ordinal (MANUAL_ENTRY before
    // NEEDS_REVIEW before VERIFIED) outranks the rest.
    private Comparator<AdminReviewItemDTO> reviewQueueOrdering() {
        return Comparator
                .comparingInt((AdminReviewItemDTO item) -> item.priorityTier().ordinal())
                .thenComparing(Comparator.comparingLong(AdminReviewItemDTO::openReportCount).reversed())
                .thenComparing(Comparator.comparingLong(AdminReviewItemDTO::confirmationCount).reversed())
                .thenComparing(item -> item.verificationStatus().ordinal());
    }
}
