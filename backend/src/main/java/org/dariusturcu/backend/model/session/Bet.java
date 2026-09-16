package org.dariusturcu.backend.model.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// One accepted bet against the active player's own shared timeline for a round: a single
// distinct gap (position), staked by a player other than the active player or DJ who
// hasn't already bet this round. The bets_round_position and bets_round_player unique
// constraints (see V16 migration) enforce one bet per gap and one bet per player.
@Entity
@Getter
@Setter
@Table(name = "bets")
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    private int position;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;
}
