package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingImportProcessorTest {

    private static final Long PENDING_IMPORT_ID = 5L;
    private static final String VIDEO_ID = "recheckId01";
    private static final int PROVISIONAL_YEAR = 1998;
    private static final int PATIENT_YEAR = 1999;

    @Mock
    private PendingImportRepository pendingImportRepository;
    @Mock
    private SongResolutionService songResolutionService;

    private PendingImport recheck() {
        PendingImport pendingImport = new PendingImport();
        pendingImport.setId(PENDING_IMPORT_ID);
        pendingImport.setYoutubeId(VIDEO_ID);
        pendingImport.setOrigin(PendingImportOrigin.FAST_TIER_RECHECK);
        pendingImport.setProvisionalYear(PROVISIONAL_YEAR);
        return pendingImport;
    }

    @Test
    void aPatientRunRecordsItsYearBesideTheProvisionalOne() {
        PendingImport pendingImport = recheck();
        Song patientSong = new Song();
        patientSong.setReleaseYear(PATIENT_YEAR);
        when(pendingImportRepository.findById(PENDING_IMPORT_ID)).thenReturn(Optional.of(pendingImport));
        when(songResolutionService.resolveAndPersist(VIDEO_ID, null)).thenReturn(Optional.of(patientSong));

        new PendingImportProcessor(pendingImportRepository, songResolutionService).process(PENDING_IMPORT_ID);

        assertThat(pendingImport.getStatus()).isEqualTo(PendingImportStatus.DONE);
        assertThat(pendingImport.getProvisionalYear()).isEqualTo(PROVISIONAL_YEAR);
        assertThat(pendingImport.getPatientYear()).isEqualTo(PATIENT_YEAR);
    }

    @Test
    void aFailedPatientRunLeavesThePatientYearEmpty() {
        PendingImport pendingImport = recheck();
        when(pendingImportRepository.findById(PENDING_IMPORT_ID)).thenReturn(Optional.of(pendingImport));
        when(songResolutionService.resolveAndPersist(VIDEO_ID, null)).thenReturn(Optional.empty());

        new PendingImportProcessor(pendingImportRepository, songResolutionService).process(PENDING_IMPORT_ID);

        assertThat(pendingImport.getStatus()).isEqualTo(PendingImportStatus.FAILED);
        assertThat(pendingImport.getPatientYear()).isNull();
    }
}
