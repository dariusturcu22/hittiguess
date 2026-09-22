package org.dariusturcu.backend.model.playlist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * One video id inside a background playlist import. PENDING until the off-thread run
 * resolves it; songId stays null for ids that never resolve to a catalog song.
 */
@Entity
@Getter
@Setter
@Table(name = "playlist_import_job_items")
public class PlaylistImportJobItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private PlaylistImportJob job;

    @Column(name = "youtube_id", nullable = false)
    private String youtubeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaylistImportJobItemStatus status = PlaylistImportJobItemStatus.PENDING;

    @Column(name = "song_id")
    private Long songId;
}
