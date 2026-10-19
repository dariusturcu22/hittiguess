package org.dariusturcu.backend.model.song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.dariusturcu.backend.model.user.User;

import java.time.Instant;

/**
 * A community report that a song's metadata is wrong, tied to the reporting user and the
 * song. A given user can hold at most one report per song, enforced by a unique constraint.
 * The suggested correct year is optional: a report can flag a problem without proposing a
 * replacement. Resolution stays manual, an admin moves the status to UPHELD or DISMISSED.
 */
@Entity
@Getter
@Setter
@Table(
        name = "song_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_song_report_reporter_song",
                columnNames = {"reporter_id", "song_id"}))
public class SongReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    private Integer suggestedCorrectYear;

    @Column(columnDefinition = "TEXT")
    private String sources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SongReportStatus status = SongReportStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
