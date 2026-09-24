package org.dariusturcu.backend.model.song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A YouTube ID waiting for the patient metadata pipeline: an admin catalog seed, or the
 * recheck of a song a user path already saved with a provisional answer. The scheduled
 * backlog drain picks up PENDING rows, moves them through PROCESSING, and lands each on
 * DONE or FAILED. A recheck keeps the provisional year, and the drain records the
 * patient tier's year beside it.
 */
@Entity
@Getter
@Setter
@Table(name = "pending_imports")
public class PendingImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "youtube_id", nullable = false)
    private String youtubeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingImportStatus status = PendingImportStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant enqueuedAt;

    private Instant processedAt;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingImportOrigin origin = PendingImportOrigin.ADMIN_SEED;

    private Integer provisionalYear;

    private Integer patientYear;
}
