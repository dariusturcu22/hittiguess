package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.model.song.Song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private Instant placementEndsAt;

    // Null until the active player locks in a placement.
    private Integer placedPosition;
    private Boolean placementCorrect;

    private Instant lockedInAt;
    private Instant bettingWindowEndsAt;

    private Instant revealedAt;
    private Instant scoredAt;

    @ElementCollection
    @CollectionTable(name = "round_betting_skips", joinColumns = @JoinColumn(name = "round_id"))
    @Column(name = "player_id")
    private Set<Long> bettingSkippedPlayerIds = new HashSet<>();

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Guess> guesses = new ArrayList<>();

    // Every accepted bet against this round's active-player timeline, at most one per
    // distinct gap and at most one per player (see V16 migration's unique constraints).
    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Bet> bets = new ArrayList<>();
}
