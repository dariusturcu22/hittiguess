package org.dariusturcu.backend.model.session;

import org.dariusturcu.backend.model.song.Song;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// One song won onto a player's timeline, either the starting anchor card or a round
// this player's placement (or a successful bet) claimed. releaseYear is denormalized
// off Song for cheap ordering/placement checks without a join.
@Entity
@Getter
@Setter
@Table(name = "player_cards")
public class PlayerCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    // Index within the owning player's timeline, 0-based, kept contiguous by
    // Player.addCard/insertCardAt.
    @Column(nullable = false)
    private int position;

    public static PlayerCard of(Song song) {
        PlayerCard card = new PlayerCard();
        card.setSong(song);
        card.setReleaseYear(song.getReleaseYear());
        return card;
    }
}
