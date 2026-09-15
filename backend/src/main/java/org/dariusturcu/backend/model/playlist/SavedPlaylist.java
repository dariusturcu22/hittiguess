package org.dariusturcu.backend.model.playlist;

import org.dariusturcu.backend.model.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// A bookmark of a public playlist into a user's own library. Distinct from PlaylistMembership:
// saving grants no read/write/delete access and creates no per-playlist identity, it only
// records that the user chose to keep this playlist in their own list.
@Entity
@Getter
@Setter
@Table(name = "saved_playlists", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "playlist_id"}))
public class SavedPlaylist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @Column(nullable = false)
    private Instant savedAt;
}
