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

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Song> songs = new ArrayList<>();

    @ManyToMany(mappedBy = "playlists")
    private Set<User> users = new HashSet<>();

    public void addSong(Song song) {
        songs.add(song);
        song.setPlaylist(this);
    }

    public void removeSong(Song song) {
        songs.remove(song);
        song.setPlaylist(null);
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
