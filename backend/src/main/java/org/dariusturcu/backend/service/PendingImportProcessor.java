package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Processes one pending import in its own transaction, so the drain commits each
 * item's status transition independently and can check the priority coordinator
 * on a committed boundary between items rather than holding one long transaction
 * across the whole batch.
 */
@Service
@RequiredArgsConstructor
public class PendingImportProcessor {

    private static final String NO_RESOLUTION_FAILURE_REASON = "Metadata pipeline returned no resolution";

    private final PendingImportRepository pendingImportRepository;
    private final SongResolutionService songResolutionService;

    @Transactional
    public void process(Long pendingImportId) {
        PendingImport pendingImport = pendingImportRepository.findById(pendingImportId).orElseThrow();

        pendingImport.setStatus(PendingImportStatus.PROCESSING);
        pendingImportRepository.save(pendingImport);

        Optional<Song> resolvedSong = songResolutionService.resolveAndPersist(pendingImport.getYoutubeId(), null);

        pendingImport.setProcessedAt(Instant.now());
        if (resolvedSong.isPresent()) {
            pendingImport.setStatus(PendingImportStatus.DONE);
            pendingImport.setPatientYear(resolvedSong.get().getReleaseYear());
        } else {
            pendingImport.setStatus(PendingImportStatus.FAILED);
            pendingImport.setFailureReason(NO_RESOLUTION_FAILURE_REASON);
        }
        pendingImportRepository.save(pendingImport);
    }
}
