package org.dariusturcu.backend.model.group;

import org.dariusturcu.backend.model.playlist.Playlist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "groups")
public class Group {

    public static final int MAX_MEMBERS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, unique = true)
    private String inviteCode;

    @Column(name = "join_code", nullable = false, updatable = false, unique = true, length = 4)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "dj_mode", nullable = false)
    private DjMode djMode;

    @Column(nullable = false)
    private int winConditionCardCount;

    @ManyToMany
    @JoinTable(
            name = "group_playlists",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "playlist_id")
    )
    private Set<Playlist> playlists = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Null while no expiry timer is running (a game session is currently in progress).
    // Set to now + the pre-session window on creation, and to now + the between-session
    // window once a session ends; the scheduled sweep deletes any group whose expiresAt
    // has passed, covering both timers through the same column.
    private Instant expiresAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Member> members = new ArrayList<>();

    public void addMember(Member member) {
        members.add(member);
        member.setGroup(this);
    }

    public void removeMember(Member member) {
        members.remove(member);
        member.setGroup(null);
    }

    public Optional<Member> getAdmin() {
        return members.stream().filter(Member::isAdmin).findFirst();
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }
}
