package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.model.group.DjMode;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// The round-by-round gameplay itself, created only when a group's admin starts a
// session (see GameSessionService.startSession, triggered off GroupService's own
// GAME_SESSION_STARTED broadcast). Every row this entity roots, players, rounds,
// guesses, and timeline cards, is ephemeral: GameSessionService.purgeSession deletes
// all of it once the session ends or is abandoned, and hands the group back to
// GroupService.recordGameSessionEnded.
@Entity
@Getter
@Setter
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, unique = true, updatable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    // Snapshotted from the group's settings at start time: later admin changes to the
    // group (impossible anyway while LOCKED) never retroactively affect a session already
    // in progress.
    @Enumerated(EnumType.STRING)
    @Column(name = "dj_mode", nullable = false)
    private DjMode djMode;

    @Column(name = "win_condition_card_count", nullable = false)
    private int winConditionCardCount;

    @Column(name = "current_round_number", nullable = false)
    private int currentRoundNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Null while at least one player is connected. Set the instant the last connected
    // player drops, cleared again the moment anyone reconnects. GameSessionScheduler
    // schedules the auto-abandon effect off this timestamp.
    private Instant zeroConnectedSince;

    // Set once at session start for DjMode.FIXED, left null for ROTATING. The player at
    // this id never enters the active-player rotation for the life of the session; see
    // GameSessionService's round-rotation helpers and DECISIONS.md.
    private Long fixedDjPlayerId;

    // Remaining song ids for future rounds, in play order, popped from the front as each
    // round starts. Populated once at session start from the group's playlists, after the
    // starting anchor cards are handed out; never replenished.
    @ElementCollection
    @CollectionTable(name = "game_session_song_queue", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "queue_position")
    @Column(name = "song_id")
    private List<Long> songQueue = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Player> players = new ArrayList<>();

    public void addPlayer(Player player) {
        players.add(player);
        player.setSession(this);
    }
}
