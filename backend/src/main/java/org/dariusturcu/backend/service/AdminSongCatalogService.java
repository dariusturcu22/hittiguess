package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.song.AdminCatalogCountsDTO;
import org.dariusturcu.backend.model.song.AdminCatalogPageDTO;
import org.dariusturcu.backend.model.song.AdminCatalogSongDTO;
import org.dariusturcu.backend.model.song.AdminQueueHistoryEntryDTO;
import org.dariusturcu.backend.model.song.AdminSongDetailDTO;
import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.SongReportStatus;
import org.dariusturcu.backend.repository.AdminCatalogRow;
import org.dariusturcu.backend.repository.AdminSongCatalogRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongReportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The admin song catalog list and the read-only song detail. Search text is trimmed and must be
 * at least {@value #MINIMUM_SEARCH_LENGTH} characters when present. Pages are 0-based and hold at
 * most {@value #MAXIMUM_PAGE_SIZE} rows; a larger size is rejected rather than clamped.
 */
@Service
@RequiredArgsConstructor
public class AdminSongCatalogService {

    public static final int MINIMUM_SEARCH_LENGTH = 3;
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAXIMUM_PAGE_SIZE = 25;
    public static final int MINIMUM_PAGE_SIZE = 1;
    public static final int FIRST_PAGE = 0;

    private static final int UNRESOLVED_RELEASE_YEAR = 0;

    private final AdminSongCatalogRepository catalogRepository;
    private final SongRepository songRepository;
    private final PendingImportRepository pendingImportRepository;
    private final SongReportRepository songReportRepository;

    /**
     * Returns the trimmed search text, or null when there is none to apply.
     *
     * @throws IllegalArgumentException when the trimmed text is shorter than the minimum
     */
    public static String normalizeSearchText(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmedQuery = query.strip();
        if (trimmedQuery.length() < MINIMUM_SEARCH_LENGTH) {
            throw new IllegalArgumentException(
                    "Search text must be at least " + MINIMUM_SEARCH_LENGTH + " characters");
        }
        return trimmedQuery;
    }

    /**
     * Returns the page size to use: the default when none is given.
     *
     * @throws IllegalArgumentException when the size is below one or above the maximum
     */
    public static int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < MINIMUM_PAGE_SIZE || size > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between " + MINIMUM_PAGE_SIZE + " and " + MAXIMUM_PAGE_SIZE);
        }
        return size;
    }

    public static int resolvePage(Integer page) {
        if (page == null) {
            return FIRST_PAGE;
        }
        if (page < FIRST_PAGE) {
            throw new IllegalArgumentException("Page must be " + FIRST_PAGE + " or greater");
        }
        return page;
    }

    public AdminCatalogPageDTO search(String query, CatalogSongStatus status, Integer page, Integer size) {
        String searchText = normalizeSearchText(query);
        int resolvedPage = resolvePage(page);
        int pageSize = resolvePageSize(size);

        Map<CatalogSongStatus, Long> countsByStatus = catalogRepository.countByStatus(searchText);
        long allCount = countsByStatus.values().stream().mapToLong(Long::longValue).sum();
        long totalElements = status == null ? allCount : countsByStatus.get(status);
        long offset = (long) resolvedPage * pageSize;

        List<AdminCatalogSongDTO> items = catalogRepository.findPage(searchText, status, pageSize, offset).stream()
                .map(AdminSongCatalogService::toCatalogSong)
                .toList();
        int totalPages = (int) ((totalElements + pageSize - 1) / pageSize);

        return new AdminCatalogPageDTO(items, resolvedPage, pageSize, totalElements, totalPages,
                toCounts(countsByStatus));
    }

    /** Counts for the whole catalog, with no search text or status filter. */
    public AdminCatalogCountsDTO catalogCounts() {
        return toCounts(catalogRepository.countByStatus(null));
    }

    private static AdminCatalogCountsDTO toCounts(Map<CatalogSongStatus, Long> countsByStatus) {
        long allCount = countsByStatus.values().stream().mapToLong(Long::longValue).sum();
        return new AdminCatalogCountsDTO(
                allCount,
                countsByStatus.get(CatalogSongStatus.VERIFIED),
                countsByStatus.get(CatalogSongStatus.NEEDS_REVIEW),
                countsByStatus.get(CatalogSongStatus.IN_QUEUE),
                countsByStatus.get(CatalogSongStatus.PROCESSING),
                countsByStatus.get(CatalogSongStatus.ERROR));
    }

    @Transactional(readOnly = true)
    public AdminSongDetailDTO detail(Long songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, songId));
        List<PendingImport> queueRows = song.getYoutubeId() == null
                ? List.of()
                : pendingImportRepository.findByYoutubeIdOrderByEnqueuedAtAscIdAsc(song.getYoutubeId());
        Set<PendingImportStatus> queueStatuses = queueRows.stream()
                .map(PendingImport::getStatus)
                .collect(Collectors.toSet());
        boolean hasAdminSeedRow = queueRows.stream()
                .anyMatch(queueRow -> queueRow.getOrigin() == PendingImportOrigin.ADMIN_SEED);
        String addedByUsername = song.getAddedBy() == null ? null : song.getAddedBy().getUsername();

        return new AdminSongDetailDTO(
                song.getId(),
                song.getYoutubeId(),
                song.getTitle(),
                song.getArtists().stream().map(SongArtist::getName).toList(),
                song.getReleaseYear() == UNRESOLVED_RELEASE_YEAR ? null : song.getReleaseYear(),
                CatalogSongRules.songStatus(song.getVerificationStatus(),
                        CatalogSongRules.activeQueueStatus(queueStatuses)),
                CatalogSongRules.pipeline(queueRows),
                song.getCreatedAt(),
                addedByUsername,
                CatalogSongRules.addedByKind(addedByUsername, hasAdminSeedRow),
                song.getVerificationStatus(),
                song.getConfidence(),
                song.getWikidataSitelinksCount(),
                songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.OPEN),
                queueRows.stream().map(AdminSongCatalogService::toHistoryEntry).toList());
    }

    private static AdminCatalogSongDTO toCatalogSong(AdminCatalogRow row) {
        return new AdminCatalogSongDTO(
                row.rowKey(),
                row.songId(),
                row.youtubeId(),
                row.title(),
                row.artists(),
                row.releaseYear(),
                row.status(),
                CatalogSongRules.pipeline(row.hasUnfinishedRecheck(), row.hasFinishedRecheck(), row.latestQueueOrigin()),
                row.createdAt(),
                row.addedByUsername(),
                CatalogSongRules.addedByKind(row.addedByUsername(), row.hasAdminSeedRow()));
    }

    private static AdminQueueHistoryEntryDTO toHistoryEntry(PendingImport queueRow) {
        return new AdminQueueHistoryEntryDTO(
                queueRow.getId(),
                queueRow.getOrigin(),
                queueRow.getStatus(),
                queueRow.getEnqueuedAt(),
                queueRow.getProcessedAt(),
                queueRow.getProvisionalYear(),
                queueRow.getPatientYear(),
                queueRow.getFailureReason());
    }
}
