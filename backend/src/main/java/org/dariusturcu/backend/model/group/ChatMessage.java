package org.dariusturcu.backend.model.group;

import org.dariusturcu.backend.model.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// A single group-scoped chat message. Scoped to the group's lifetime, not a game
// session's: it is created once a group exists and removed when the group is deleted,
// through the database-level cascade on the group foreign key. The content column caps
// at the same length the send path validates against.
@Entity
@Getter
@Setter
@Table(name = "chat_messages")
public class ChatMessage {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
