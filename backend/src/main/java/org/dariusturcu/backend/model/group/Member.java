package org.dariusturcu.backend.model.group;

import org.dariusturcu.backend.model.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// A member's display name and avatar are per-group, distinct from the account
// profile on User, and are the only identity ever exposed through group-scoped
// endpoints. The unique constraint on user_id enforces one active membership
// per user across every group, not just within a single one: an explicit leave
// removes this row, a disconnect never does.
@Entity
@Getter
@Setter
@Table(name = "members", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String displayName;

    private String avatarUrl;

    @Column(nullable = false)
    private boolean isAdmin;

    @Column(nullable = false)
    private boolean isConnected;

    @Column(nullable = false)
    private boolean isInVoice;

    // Set when the member's socket drops and cleared when it returns; the voice sweep
    // reads it to remove a member whose tab closed while they were in the call.
    private Instant disconnectedAt;

    // Reported by the member's browser; false keeps them out of the DJ rotation because
    // they cannot capture the song's audio for the others.
    @Column(nullable = false)
    private boolean canShareAudio = true;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;
}
