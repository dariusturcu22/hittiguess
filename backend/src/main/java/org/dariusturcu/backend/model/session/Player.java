package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.model.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// A session-scoped participant, snapshotted from a group Member when the session starts.
// displayName/avatarUrl are copied rather than referenced live, the same reasoning
// Member itself already documents: this identity is frozen for the life of the session,
// independent of anything the account or group-membership row does afterward.
@Entity
@Getter
@Setter
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String displayName;

    private String avatarUrl;

    // Fixed turn-rotation position assigned at roster snapshot time, 0-indexed. Active
    // player and (in ROTATING dj mode) DJ assignment both walk this ordering, skipping
    // any player whose status is LEFT.
    @Column(name = "turn_order", nullable = false)
    private int turnOrder;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayerStatus status;

    @Column(name = "is_connected", nullable = false)
    private boolean isConnected;

    // Null while connected. Set the instant this player disconnects; the turn-timeout
    // effect only fires if this player is still the active player 90 seconds later and
    // this field is still set (a reconnect clears it, cancelling the timeout in effect).
    private Instant disconnectedAt;

    @Column(name = "total_artists_guessed", nullable = false)
    private int totalArtistsGuessed;

    @Column(name = "total_titles_guessed", nullable = false)
    private int totalTitlesGuessed;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 20)
    private List<PlayerCard> timeline = new ArrayList<>();

    public void addCard(PlayerCard card) {
        card.setPlayer(this);
        card.setPosition(timeline.size());
        timeline.add(card);
    }

    public void insertCardAt(PlayerCard card, int position) {
        card.setPlayer(this);
        timeline.add(position, card);
        for (int index = 0; index < timeline.size(); index++) {
            timeline.get(index).setPosition(index);
        }
    }

    public boolean hasWon(int winConditionCardCount) {
        return timeline.size() >= winConditionCardCount;
    }
}
