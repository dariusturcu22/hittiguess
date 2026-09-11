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

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Song> songs = new ArrayList<>();

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<PlaylistMembership> memberships = new ArrayList<>();

    public void addSong(Song song) {
        songs.add(song);
        song.setPlaylist(this);
    }

    public void removeSong(Song song) {
        songs.remove(song);
        song.setPlaylist(null);
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
