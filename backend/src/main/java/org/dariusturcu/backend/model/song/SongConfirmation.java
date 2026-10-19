package org.dariusturcu.backend.model.song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.dariusturcu.backend.model.user.User;

import java.time.Instant;

/**
 * A community thumbs-up that a song's metadata is correct, tied to the confirming user and
 * the song. A given user can hold at most one confirmation per song, enforced by a unique
 * constraint. Distinct from a report: a confirmation is the positive signal, ranked into the
 * admin review queue only for songs that have not reached the VERIFIED tier.
 */
@Entity
@Getter
@Setter
@Table(
        name = "song_confirmations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_song_confirmation_user_song",
                columnNames = {"user_id", "song_id"}))
public class SongConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
