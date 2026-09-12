package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.model.song.Song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "rounds")
public class Round {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @ManyToOne
    @JoinColumn(name = "active_player_id", nullable = false)
    private Player activePlayer;

    @ManyToOne
    @JoinColumn(name = "dj_player_id", nullable = false)
    private Player djPlayer;

    @ManyToOne
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status;

    // Null until the active player locks in a placement.
    private Integer placedPosition;
    private Boolean placementCorrect;

    private Instant lockedInAt;
    private Instant bettingWindowEndsAt;

    // Set only through RoundRepository's atomic conditional UPDATE (tryAcceptBet):
    // the first bet wins, everyone else's attempt leaves this column untouched.
    @ManyToOne
    @JoinColumn(name = "bettor_player_id")
    private Player bettorPlayer;

    private Instant betPlacedAt;
    private Instant revealedAt;
    private Instant scoredAt;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Guess> guesses = new ArrayList<>();

    public boolean hasBettor() {
        return bettorPlayer != null;
    }
}
