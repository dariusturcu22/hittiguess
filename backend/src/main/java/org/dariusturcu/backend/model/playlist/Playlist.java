package org.dariusturcu.backend.model.playlist;

import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @ManyToMany(mappedBy = "playlists")
    private Set<User> users = new HashSet<>();

    public void addSong(Song song) {
        songs.add(song);
        song.getPlaylists().add(this);
    }

    public void removeSong(Song song) {
        songs.remove(song);
        song.getPlaylists().remove(this);
    }

    public void addUser(User user) {
        users.add(user);
    }

    public void removeUser(User user) {
        users.remove(user);
    }

    public int getUserCount() {
        return users.size();
    }

    public int getSongCount() {
        return songs.size();
    }
}
