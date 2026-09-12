package org.dariusturcu.backend.model.playlist;

import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
@Table(name = "playlists")
public class Playlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String color;

    @Column(nullable = false, updatable = false, unique = true)
    private String inviteCode;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Owning side of the join table: a song can belong to more than one playlist, so removing
    // it here only unlinks it, it never cascades to deleting the Song row itself (PlaylistService
    // decides that separately, based on whether the song is left in any playlist at all).
    @ManyToMany
    @JoinTable(
            name = "song_playlists",
            joinColumns = @JoinColumn(name = "playlist_id"),
            inverseJoinColumns = @JoinColumn(name = "song_id")
    )
    @OrderBy("id ASC")
    @BatchSize(size = 20)
    private List<Song> songs = new ArrayList<>();

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<PlaylistMembership> memberships = new ArrayList<>();

    public void addSong(Song song) {
        songs.add(song);
        song.getPlaylists().add(this);
    }

    public void removeSong(Song song) {
        songs.remove(song);
        song.getPlaylists().remove(this);
    }

    public void addMembership(PlaylistMembership membership) {
        memberships.add(membership);
        membership.setPlaylist(this);
    }

    public void removeMembership(PlaylistMembership membership) {
        memberships.remove(membership);
        membership.setPlaylist(null);
    }

    public boolean isOwnedBy(User user) {
        return owner != null && user != null && owner.getId().equals(user.getId());
    }

    public int getUserCount() {
        return memberships.size();
    }

    public int getSongCount() {
        return songs.size();
    }
}
