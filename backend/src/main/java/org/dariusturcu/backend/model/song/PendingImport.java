package org.dariusturcu.backend.model.song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Retained import history, including provisional and patient years for user rechecks. */
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
