package org.dariusturcu.backend.model.song;

import java.time.Instant;

// One provisional answer queued for the patient tier, as the admin backlog shows it:
// the song as it stands now, the provisional year it was saved with, and the patient
// tier's year once the drain has run it.
public record PatientRecheckDTO(
        String youtubeId,
        PendingImportOrigin origin,
        PendingImportStatus status,
        String title,
        String artists,
        Integer provisionalYear,
        Integer patientYear,
        Instant enqueuedAt,
        Instant processedAt) {
}
