package org.dariusturcu.backend.model.playlist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One user-started background import into a playlist. The HTTP call returns as soon as
 * the row exists; resolution runs off-thread and each video id lands on the matching
 * PlaylistImportJobItem row, which the detail view reads to render pending songs.
 */
@Entity
@Getter
@Setter
@Table(name = "playlist_import_jobs")
public class PlaylistImportJob {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @Column(name = "submitted_by_username", nullable = false)
    private String submittedByUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaylistImportJobStatus status = PlaylistImportJobStatus.RUNNING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
