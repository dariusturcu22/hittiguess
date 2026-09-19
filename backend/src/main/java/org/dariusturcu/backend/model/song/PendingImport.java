package org.dariusturcu.backend.model.song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A YouTube ID the admin submitted for eventual catalog seeding, not yet resolved.
 * The scheduled backlog drain picks up PENDING rows, moves them through PROCESSING,
 * and lands each on DONE or FAILED. Distinct from the user on-the-spot path, which
 * never enqueues here and resolves immediately.
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
}
