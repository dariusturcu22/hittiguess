package org.dariusturcu.backend.model.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// One artist/title guess submission. Every player but the round's DJ may submit one for
// the whole turn; only the active player's fully-correct guess (isArtistCorrect and
// isTitleCorrect both true) awards a token, but every guess, active or not, feeds the
// two session-long tallies on Player.
@Entity
@Getter
@Setter
@Table(name = "guesses")
public class Guess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    private String guessedArtist;
    private String guessedTitle;

    @Column(name = "is_artist_correct", nullable = false)
    private boolean artistCorrect;

    @Column(name = "is_title_correct", nullable = false)
    private boolean titleCorrect;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isFullyCorrect() {
        return artistCorrect && titleCorrect;
    }
}
